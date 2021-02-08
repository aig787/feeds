package com.devo.feeds

import com.devo.feeds.data.misp.FeedAndTag
import com.devo.feeds.feed.CSVFeed
import com.devo.feeds.feed.Feed
import com.devo.feeds.feed.FeedException
import com.devo.feeds.feed.FeedSpec
import com.devo.feeds.feed.MispFeed
import com.devo.feeds.output.AttributeOutput
import com.devo.feeds.output.EventUpdate
import com.devo.feeds.storage.AttributeCache
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.util.KtorExperimentalAPI
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

class FeedsService(private val config: Config) {

    data class FeedJob(val config: FeedAndTag, val job: Job)

    private val log = KotlinLogging.logger { }
    private var running = false
    private val format = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private lateinit var configJob: Job
    private lateinit var configuredFeeds: List<FeedAndTag>

    @KtorExperimentalAPI
    private val httpClient = HttpClient(CIO) {
        engine {
            threadsCount = config.getInt("feeds.http.client.threads")
            requestTimeout
        }
    }

    private val attributeCache = getAttributeCache()
    private val feedUpdateInterval = config.getDuration("feeds.feedUpdateInterval")
    private var outputs: List<AttributeOutput> = emptyList()
    private val jobLock = ReentrantLock()
    private var jobs = mapOf<FeedAndTag, FeedJob>()

    /**
     * Start Feeds process
     *
     * Read feeds config from MISP instance, start [Flow] in parallel for each, and start a flow to monitor and update
     * running flows on config change.
     *
     */
    @FlowPreview
    @ObsoleteCoroutinesApi
    @KtorExperimentalAPI
    suspend fun run() {
        running = true
        outputs = getOutputs()
        log.info { "Starting Feeds process" }

        configuredFeeds = getConfiguredFeeds()

        val duration = config.getDuration("feeds.mispUpdateInterval")
        log.info { "Running config update every ${duration.seconds} seconds" }
        configJob = configFlow().launchIn(GlobalScope)

        log.info { "Starting feed flows" }
        jobLock.withLock {
            jobs = startFeeds(configuredFeeds).associateBy { it.config }
        }
        while (running) {
            // Continue joining what's there until marked as stopped
            jobs.forEach { it.value.job.join() }
        }
        log.info { "Feed flow stopped" }
    }

    /**
     * Stop main process and all children
     */
    fun stop() {
        if (running) {
            runBlocking {
                log.info { "Stopping Feeds process" }
                configJob.cancel()
                configJob.join()
                running = false
                jobs.values.forEach {
                    it.job.cancel()
                    it.job.join()
                }
                attributeCache.close()
            }
        }
    }

    /**
     * Write [update] to each configured output
     */
    private suspend fun writeOutputsAsync(feed: String, update: EventUpdate) = coroutineScope {
        async {
            log.info { "Writing ${update.newAttributes.size} attributes for ${update.event.uuid}" }
            outputs.forEach {
                log.info { "Writing ${update.event.uuid}" }
                it.write(feed, update)
            }
        }
    }

    @FlowPreview
    @ObsoleteCoroutinesApi
    @KtorExperimentalAPI
    /**
     * Build config monitoring flow
     */
    private fun configFlow(): Flow<Unit> {
        val duration = config.getDuration("feeds.mispUpdateInterval")
        return ticker(duration.toMillis(), duration.toMillis()).receiveAsFlow().onEach {
            log.info { "Pulling config from MISP" }
            val fetchedFeeds = getConfiguredFeeds().also {
                log.info { "Found ${it.size} configured feeds" }
            }.toSet()
            val currentFeeds = jobs.keys

            // If new feeds are different than what's running, make changes
            if (currentFeeds != fetchedFeeds) {
                val oldFeeds = currentFeeds
                    .minus(fetchedFeeds).map { feedConfig -> feedConfig to jobs[feedConfig]!! }
                    .toMap()

                jobLock.withLock {
                    val newFeeds = fetchedFeeds.minus(currentFeeds)
                    log.info { "Starting ${newFeeds.size} new or updated feeds" }
                    val newJobs = newFeeds.mapNotNull { feedConfig ->
                        feedFromConfig(feedConfig, feedUpdateInterval)
                    }.associate { (feedConfig, feed) ->
                        feedConfig to feedFlow(feed, feedConfig, feedUpdateInterval)
                    }

                    // Set jobs to the jobs we know about that didn't change plus the new ones
                    jobs = jobs.minus(oldFeeds.keys).plus(newJobs)
                }

                // Stop old feeds
                log.info { "Stopping ${oldFeeds.size} outdated feeds" }
                oldFeeds.values.forEach {
                    it.job.cancel()
                    it.job.join()
                }
            }
        }
    }

    @ObsoleteCoroutinesApi
    @FlowPreview
    @KtorExperimentalAPI
    /**
     * Create flows for all [fetchedFeeds] and start the jobs
     */
    private fun startFeeds(fetchedFeeds: List<FeedAndTag>): List<FeedJob> {
        return fetchedFeeds.mapNotNull { feedAndTag ->
            feedFromConfig(feedAndTag, feedUpdateInterval)
        }.map { (feedConfig, feed) ->
            feedFlow(feed, feedConfig, feedUpdateInterval)
        }
    }

    @FlowPreview
    @ObsoleteCoroutinesApi
    @Suppress("TooGenericExceptionCaught")
    /**
     *
     */
    private fun feedFlow(
        feed: Feed,
        config: FeedAndTag,
        interval: Duration
    ): FeedJob = FeedJob(
        config = config,
        job = ticker(interval.toMillis(), 0).receiveAsFlow().cancellable()
            .flatMapMerge {
                try {
                    log.info { "Processing feed ${feed.name}" }
                    feed.run()
                } catch (fe: FeedException) {
                    log.error { "Failed to fetch feed ${config.feed.name}: ${fe.message}" }
                    emptyFlow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error(e) { "Failed to fetch feed ${config.feed.name}" }
                    emptyFlow()
                }
            }.map { update ->
                writeOutputsAsync(feed.name, update).invokeOnCompletion {
                    if (it == null) {
                        val attrs = update.newAttributes
                        log.info {
                            "Marking ${attrs.size} attributes for feed: ${feed.name}," +
                                    "event: ${update.event.uuid} as sent"
                        }
                        attrs.forEach { attr ->
                            feed.markAttributeSent(update.event.id.toString(), attr.uuid!!)
                        }
                    } else {
                        log.error(it) { "Failed to write to Devo" }
                    }
                }
            }.launchIn(GlobalScope)
    )

    @KtorExperimentalAPI
    @ObsoleteCoroutinesApi
    private fun feedFromConfig(config: FeedAndTag, interval: Duration): Pair<FeedAndTag, Feed>? =
        if (config.feed.enabled) {
            val feedSpec = FeedSpec(config.feed.name, interval, config.feed.url, config.tag, attributeCache)
            when (config.feed.sourceFormat.toLowerCase()) {
                "misp" -> config to MispFeed(feedSpec, httpClient)
                "csv" -> config to CSVFeed.fromConfig(config, feedSpec, httpClient)
                else -> {
                    log.info { "Ignoring unsupported feed ${config.feed.name} with type ${config.feed.sourceFormat}" }
                    null
                }
            }
        } else {
            log.info { "Ignoring disabled feed ${config.feed.name}" }
            null
        }

    private fun getOutputs(): List<AttributeOutput> =
        config.getConfigList("feeds.outputs").map { outputConfig ->
            val className = outputConfig.getString("class")
            log.info { "Creating output with type $className" }
            (Class.forName(className).getConstructor().newInstance() as AttributeOutput)
                .build(outputConfig)
        }

    @KtorExperimentalAPI
    internal suspend fun getConfiguredFeeds(): List<FeedAndTag> {
        val mispUrl = config.getString("feeds.misp.url")
        log.info { "Fetching config from $mispUrl/feeds" }
        val response = httpClient.get<String>("$mispUrl/feeds") {
            headers {
                append("Authorization", config.getString("feeds.misp.key"))
                append("Accept", "application/json")
            }
        }
        return format.decodeFromString(response)
    }

    private fun getAttributeCache(): AttributeCache {
        val className = config.getString("feeds.cache.class")
        log.info { "Using cache $className" }
        return (Class.forName(className).getConstructor().newInstance() as AttributeCache)
            .build(config.getConfig("feeds.cache"))
    }
}

@FlowPreview
@ObsoleteCoroutinesApi
@KtorExperimentalAPI
fun main() {
    val feeds = FeedsService(ConfigFactory.load())
    val log = KotlinLogging.logger { }
    val handler = CoroutineExceptionHandler { _, exception ->
        log.error(exception) { "Error in coroutine" }
    }
    runBlocking(handler) {
        feeds.run()
    }
}
