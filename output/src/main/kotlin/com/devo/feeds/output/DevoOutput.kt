package com.devo.feeds.output

import com.devo.feeds.data.X509Credentials
import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.data.misp.MispObject
import com.devo.feeds.data.misp.Tag
import com.typesafe.config.Config
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

@Serializable
data class DevoMispAttribute(
    @SerialName("Attribute") val attribute: Attribute,
    @SerialName("Event") val event: Event,
    @SerialName("Object") val mispObject: MispObject? = null,
    @SerialName("EventTags") val eventTags: Set<Tag> = emptySet()
)

class DevoOutputFactory : OutputFactory<DevoOutput> {
    override fun fromConfig(config: Config): DevoOutput {
        val host = config.getString("host")
        val port = config.getInt("port")
        val credentials = X509Credentials(
            config.getString("keystore"),
            config.getString("keystorePass"),
            mapOf("chain" to config.getString("chain"))
        )
        val threads = config.getIntOrDefault("threads", 1)
        return DevoOutput(host, port, credentials, threads)
    }
}

open class DevoOutput(host: String, port: Int, credentials: X509Credentials?, threads: Int) :
    SyslogOutput(host, port, listOf("threatintel.misp.attributes"), credentials, threads) {

    private val log = KotlinLogging.logger { }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun serializeDevoAttribute(attribute: DevoMispAttribute): String {
        log.debug { "Serializing event:${attribute.event.uuid} attribute:${attribute.attribute.uuid}" }
        return Json.encodeToString(attribute)
    }

    private fun getDevoAttributesFromEvent(
        eventUpdate: EventUpdate
    ): List<DevoMispAttribute> = eventUpdate.newAttributes.map { attr ->
        DevoMispAttribute(
            attribute = attr,
            event = eventUpdate.event.copy(attributes = emptyList()),
            eventTags = eventUpdate.event.tags
        )
    }.onEach {
        log.trace { "Created Devo Attribute for event: ${it.event.uuid}, attribute:${it.attribute.uuid}" }
    }

    @InternalCoroutinesApi
    @ObsoleteCoroutinesApi
    override suspend fun write(feed: String, eventUpdate: EventUpdate) {
        getDevoAttributesFromEvent(eventUpdate).map {
            submit(feed, serializeDevoAttribute(it))
        }
    }
}
