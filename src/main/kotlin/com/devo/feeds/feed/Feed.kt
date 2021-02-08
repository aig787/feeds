package com.devo.feeds.feed

import com.devo.feeds.data.TypeInference
import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.data.misp.Tag
import com.devo.feeds.output.EventUpdate
import com.devo.feeds.storage.AttributeCache
import com.fasterxml.uuid.Generators
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

@ObsoleteCoroutinesApi
abstract class Feed(spec: FeedSpec) {

    companion object {
        private val uuidGenerator = Generators.nameBasedGenerator()
        fun generateAttributeUUID(eventId: String, attribute: Attribute): UUID {
            val content = "$eventId${attribute.value}${attribute.timestamp}"
            return uuidGenerator.generate(content)
        }

        fun generateEventUUID(event: Event): UUID {
            val content = "${event.id}${event.info}${event.timestamp}${event.orgId}"
            return uuidGenerator.generate(content)
        }
    }

    abstract suspend fun pull(): Flow<Event>

    private val log = KotlinLogging.logger { }
    private val attributeCache = spec.attributeCache
    private val withDefaultTag: (Event) -> Event = run {
        when (spec.defaultTag) {
            null -> { e -> e }
            else -> { e -> e.copy(tags = e.tags + spec.defaultTag) }
        }
    }

    val name = spec.name
    val url = spec.url

    suspend fun run(): Flow<EventUpdate> = pull()
        .map { ensureIds(it) }
        .map { ensureTypes(it) }
        .map { copyTags(withDefaultTag(it)) }
        .onEach { log.trace { "Ensured ids for ${Json.encodeToString(it.copy(attributes = emptyList()))}" } }
        .map { it to getNewAttributes(it) }
        .onEach { log.debug { "Found ${it.second.size} new attributes for ${it.first.uuid}" } }
        .filterNot { (_, newAttributes) -> newAttributes.isEmpty() }
        .map { (event, attributes) -> EventUpdate(event, attributes) }
        .onEach { log.debug { "Will send ${Json.encodeToString(it)}" } }

    private fun getNewAttributes(event: Event): List<Attribute> =
        event.attributes.filterNot { attr ->
            attributeCache.attributeHasSent(name, event.id!!, attr.uuid!!)
        }

    fun markAttributeSent(eventId: String, uuid: String) {
        attributeCache.markAttributeSent(name, eventId, uuid)
    }

    private fun copyTags(event: Event): Event =
        if (event.tags.isNotEmpty()) {
            event.copy(attributes = event.attributes.map { attr ->
                attr.copy(tags = event.tags + attr.tags)
            })
        } else {
            event
        }

    private fun ensureTypes(event: Event): Event =
        event.copy(attributes = event.attributes.map {
            val x = it.copy(type = it.type ?: TypeInference.inferType(it.value))
            x
        })

    internal fun ensureIds(event: Event): Event {
        val eventWithIds = ensureEventIDs(event)
        return eventWithIds.copy(attributes = eventWithIds.attributes.map {
            ensureAttributeIDs(
                eventWithIds.id!!,
                it
            )
        })
    }

    internal fun ensureAttributeIDs(eventId: String, attribute: Attribute): Attribute {
        val uuid = when (attribute.uuid) {
            null -> generateAttributeUUID(eventId, attribute).toString()
            else -> attribute.uuid
        }
        val id = when (attribute.id) {
            null -> attributeCache.getAttributeId(name, eventId, uuid!!).toString()
            else -> attribute.id
        }
        return attribute.copy(uuid = uuid, id = id, eventId = eventId)
    }

    internal fun ensureEventIDs(event: Event): Event {
        val uuid = when (event.uuid) {
            null -> generateEventUUID(event).toString()
            else -> event.uuid
        }
        val id = when (event.id) {
            null -> attributeCache.getEventId(name, uuid!!).toString()
            else -> event.id
        }
        return event.copy(uuid = uuid, id = id)
    }
}

data class FeedSpec(
    val name: String,
    val schedule: Duration,
    val url: String,
    val defaultTag: Tag?,
    val attributeCache: AttributeCache
)

enum class FeedFormat {
    MISP, CSV, FREETEXT, UNKNOWN
}

class FeedException(message: String, cause: Throwable?) : RuntimeException(message, cause)
