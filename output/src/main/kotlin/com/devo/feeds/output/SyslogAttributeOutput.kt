package com.devo.feeds.output

import com.cloudbees.syslog.sender.TcpSyslogMessageSender
import com.devo.feeds.data.X509Credentials
import com.typesafe.config.Config
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

open class SyslogAttributeOutput : AttributeOutput {

    private val log = KotlinLogging.logger { }

    private lateinit var host: String
    private lateinit var writeContext: CoroutineContext
    private lateinit var writeJobs: List<Job>
    private val messageChannel = Channel<Pair<String, String>>()

    private var port by Delegates.notNull<Int>()
    protected open lateinit var tags: List<String>
    protected var credentials: X509Credentials? = null

    @ObsoleteCoroutinesApi
    override fun build(config: Config): AttributeOutput {
        val host = config.getString("host")
        val port = config.getInt("port")
        val tags = config.getStringList("tags")
        val threads = config.getInt("threads")
        val credentials = if (config.hasPath("keystore") && config.hasPath("keystorePass")) {
            log.info { "Using TLS" }
            val trustedCerts = if (config.hasPath("chain")) {
                mapOf("chain" to config.getString("chain"))
            } else emptyMap()
            X509Credentials(config.getString("keystore"), config.getString("keystorePass"), trustedCerts)
        } else {
            log.info { "Using plaintext" }
            null
        }
        return build(host, port, tags, credentials, threads)
    }

    @ObsoleteCoroutinesApi
    fun build(
        host: String,
        port: Int,
        tags: List<String>,
        credentials: X509Credentials?,
        threads: Int
    ): SyslogAttributeOutput {
        this.host = host
        this.port = port
        this.credentials = credentials
        this.tags = tags
        writeContext = newFixedThreadPoolContext(threads, "syslog-write-context")
        GlobalScope.launch {
            writeJobs = (0 until threads).map { launchWriter(it) }
        }
        return this
    }

    private fun TcpSyslogMessageSender.sendMessageWithTag(tag: String, message: String) {
        this.defaultAppName = tag
        sendMessage(message)
        this.defaultAppName = null
    }

    protected open fun tagsForFeed(feed: String): Iterable<String> = tags

    protected suspend fun submit(feed: String, message: String) = messageChannel.send(feed to message)

    private fun CoroutineScope.launchWriter(id: Int) = launch(writeContext) {
        log.info { "Connecting to syslog at $host:$port with writer $id" }
        TcpSyslogMessageSender().apply {
            syslogServerHostname = host
            syslogServerPort = port
            credentials?.let {
                isSsl = true
                sslContext = it.sslContext
            }
        }.use { sender ->
            for ((feed, msg) in messageChannel) {
                for (tag in tagsForFeed(feed)) {
                    sender.sendMessageWithTag(tag, msg)
                }
            }
        }
    }

    override suspend fun write(feed: String, eventUpdate: EventUpdate) {
        log.info { "Writing feed: $feed, event: ${eventUpdate.event.uuid}" }
        submit(feed, Json.encodeToString(eventUpdate))
    }

    override fun close() {
        runBlocking {
            writeJobs.forEach { it.cancelAndJoin() }
        }
    }
}
