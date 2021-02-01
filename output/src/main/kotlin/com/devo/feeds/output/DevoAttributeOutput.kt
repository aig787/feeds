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

open class DevoAttributeOutput : SyslogAttributeOutput() {

    private val log = KotlinLogging.logger { }
    override var tags: List<String> = listOf("threatintel.misp.attributes")

    @ObsoleteCoroutinesApi
    override fun build(config: Config): AttributeOutput {
        val host = config.getString("host")
        val port = config.getInt("port")
        val credentials = X509Credentials(
            config.getString("keystore"),
            config.getString("keystorePass"),
            mapOf("chain" to config.getString("chain"))
        )
        val threads = config.getInt("threads")
        return build(host, port, credentials, threads)
    }

    @ObsoleteCoroutinesApi
    fun build(host: String, port: Int, credentials: X509Credentials?, threads: Int): AttributeOutput =
        build(host, port, tags, credentials, threads)

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
