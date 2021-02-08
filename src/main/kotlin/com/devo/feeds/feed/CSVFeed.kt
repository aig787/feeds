package com.devo.feeds.feed

import com.devo.feeds.data.TypeInference
import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.data.misp.FeedAndTag
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

@ObsoleteCoroutinesApi
@KtorExperimentalAPI
class CSVFeed(
    spec: FeedSpec,
    private val columns: List<Int>?,
    private val eventId: String,
    private val delimiter: String = ",",
    private val httpClient: HttpClient = HttpClient(CIO)
) : Feed(spec) {

    companion object {
        fun fromConfig(config: FeedAndTag, spec: FeedSpec, httpClient: HttpClient): CSVFeed {
            val settings =
                config.feed.settings?.let { Json.parseToJsonElement(it).jsonObject } ?: buildJsonObject { }
            val csvSettings = settings["csv"]?.jsonObject ?: buildJsonObject { }
            val delimiter = when (val d = csvSettings["delimiter"]?.jsonPrimitive?.content) {
                null -> null
                "" -> null
                else -> d
            } ?: ","
            val columns = when (val c = csvSettings["value"]?.jsonPrimitive?.content) {
                null -> null
                "" -> null
                else -> c.split(",").mapNotNull { it.toIntOrNull() }
            }
            return CSVFeed(spec, columns, config.feed.eventId!!, delimiter, httpClient)
        }
    }

    private val log = KotlinLogging.logger { }

    override suspend fun pull(): Flow<Event> =
        flowOf(fetchSingletonAttributeFile())
            .map { line ->
                parse(line).flatMap { row -> rowToAttribute(row) }
            }
            .map {
                Event(id = eventId, attributes = it)
            }

    private suspend fun fetchSingletonAttributeFile(): String = coroutineScope {
        log.info { "Fetching attributes from $url" }
        try {
            httpClient.get(url)
        } catch (e: ClientRequestException) {
            throw FeedException("Failed to fetch feed $name", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun parse(body: String): List<List<String>> =
        if (body.contains('\n')) {
            body.split("\n")
                .filterNot { it.isBlank() || it.startsWith("#") }
                .map { row ->
                    val split = row.split(delimiter)
                        .map { it.trim().replace(Regex("^\"|\"$"), "") }
                    // 1 indexed on MISP side
                    try {
                        columns?.map { split[it - 1] } ?: split
                    } catch (e: Exception) {
                        log.error(e) { "Failed to parse columns $columns for $split" }
                        emptyList()
                    }
                }
        } else {
            body.split(delimiter).map { listOf(it) }
        }

    private fun rowToAttribute(row: List<String>): List<Attribute> = when {
        row.isEmpty() -> emptyList()
        row.size == 1 -> listOf(
            Attribute(
                eventId = eventId,
                value = row.first(),
                type = TypeInference.inferType(row.first())
            )
        )
        row.size != columns?.size -> {
            log.warn { "Row $row does not match expected format" }
            emptyList()
        }
        else -> {
            // Send each as a separate attribute
            row.map {
                Attribute(eventId = eventId, value = it, type = TypeInference.inferType(it))
            }
        }
    }
}
