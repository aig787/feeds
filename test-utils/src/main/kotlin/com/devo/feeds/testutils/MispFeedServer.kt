package com.devo.feeds.testutils

import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.data.misp.EventResponse
import com.devo.feeds.data.misp.FeedAndTag
import com.devo.feeds.data.misp.FeedConfig
import com.devo.feeds.data.misp.ManifestEvent
import com.devo.feeds.data.misp.Tag
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MispFeedServer {
    companion object {
        const val DEFAULT_FEED_COUNT = 3
        const val DEFAULT_MANIFEST_EVENTS = 5
        const val DEFAULT_CSV_EVENTS = 5
        const val DEFAULT_ATTRIBUTES_PER_EVENT = 5
        const val DEFAULT_TIMEOUT_MILLIS = 1000L
    }

    val port = NetworkUtils.findAvailablePort()
    var feedCount = DEFAULT_FEED_COUNT
    var manifestEvents = DEFAULT_MANIFEST_EVENTS
    var csvEvents = DEFAULT_CSV_EVENTS
    var attributesPerEvent = DEFAULT_ATTRIBUTES_PER_EVENT

    var feeds = (0 until feedCount).map {
        val id = it.toString()
        FeedAndTag(
            FeedConfig(
                id = id,
                name = id,
                provider = id,
                url = "http://localhost:$port/$id",
                enabled = true,
                sourceFormat = "misp"
            ),
            Tag(id = id)
        )
    }
    var manifest = (0 until manifestEvents).map { it.toString() to ManifestEvent() }.toMap()
    var csv = (0 until csvEvents).joinToString("\n") { it.toString() }

    private val server = embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/{feed}/{name}.json") {
                val feed = call.parameters["feed"]
                when (val name = call.parameters["name"]) {
                    "manifest" -> call.respondText(Json.encodeToString(manifest), ContentType.Application.Json)
                    else -> {
                        val id = "$feed-$name"
                        val attributes =
                            (0 until attributesPerEvent).map {
                                Attribute(
                                    id = "$id-$it",
                                    uuid = "$id-$it",
                                    tags = setOf(Tag(id = "$id-$it"))
                                )
                            }
                        val response =
                            EventResponse(
                                Event(
                                    id = id,
                                    uuid = id,
                                    attributes = attributes,
                                    tags = setOf(Tag(id = id))
                                )
                            )
                        call.respondText(Json.encodeToString(response), ContentType.Application.Json)
                    }
                }
            }
            get("/attributes.csv") {
                call.respondText(csv, ContentType.Application.Json)
            }
            get("/feeds") {
                call.respondText(Json.encodeToString(feeds), ContentType.Application.Json)
            }
        }
    }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS)
    }
}
