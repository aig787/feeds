package com.devo.feeds.feed

import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.output.Output
import com.devo.feeds.storage.AttributeCache
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.isNullOrBlank
import io.mockk.every
import io.mockk.mockk
import java.net.SocketException
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedTest {

    @ObsoleteCoroutinesApi
    private val blankEventUUID = Feed.generateEventUUID(Event()).toString()

    class Fixture {
        val output = mockk<Output>()
        val cache = mockk<AttributeCache>()

        @ObsoleteCoroutinesApi
        val feed = MockFeed(cache)
    }

    @ObsoleteCoroutinesApi
    private fun expectCacheLookups(event: Event, cache: AttributeCache, feed: MockFeed) {
        val expectedEventUUID = Feed.generateEventUUID(event).toString()
        every { cache.getEventId(feed.name, expectedEventUUID) } returns 5L
        event.attributes.forEachIndexed { i, attr ->
            val expectedAttrUUID = Feed.generateAttributeUUID("5", attr).toString()
            every { cache.getAttributeId(feed.name, "5", expectedAttrUUID) } returns 5L + i
        }
    }

    @ObsoleteCoroutinesApi
    @Test
    fun `Should add ids to event`() {
        val f = Fixture()
        val expectedEvent = Event(id = "1", uuid = blankEventUUID)
        every { f.cache.getEventId(f.feed.name, expectedEvent.uuid!!) } returns 1L
        assertThat(f.feed.ensureEventIDs(Event()), equalTo(expectedEvent))
    }

    @ObsoleteCoroutinesApi
    @Test
    fun `Should not overwrite existing event ids`() {
        val f = Fixture()
        val event = Event(id = "2", uuid = "uuid1")
        assertThat(f.feed.ensureEventIDs(event), equalTo(event))
    }

    @ObsoleteCoroutinesApi
    @Test
    fun `Should add ids to attribute`() {
        val f = Fixture()
        val attribute = Attribute()
        val eventId = UUID.randomUUID().toString()
        val expectedAttribute =
            Attribute(eventId = eventId, uuid = Feed.generateAttributeUUID(eventId, attribute).toString(), id = "2")
        every { f.cache.getAttributeId(f.feed.name, eventId, expectedAttribute.uuid!!) } returns 2L
        assertThat(f.feed.ensureAttributeIDs(eventId, Attribute()), equalTo(expectedAttribute))
    }

    @ObsoleteCoroutinesApi
    @Test
    fun `Should not overwrite existing attribute ids`() {
        val f = Fixture()
        val attribute = Attribute(eventId = "1", uuid = "2", id = "3")
        assertThat(f.feed.ensureAttributeIDs("1", attribute), equalTo(attribute))
    }

    @ObsoleteCoroutinesApi
    @Test
    fun `Should overwrite eventId in attribute`() {
        val f = Fixture()
        val eventId = UUID.randomUUID().toString()
        val attribute = Attribute(eventId = "1", uuid = "2", id = "3")
        val expectedAttribute = attribute.copy(eventId = eventId)
        assertThat(f.feed.ensureAttributeIDs(eventId, attribute), equalTo(expectedAttribute))
    }

    @ObsoleteCoroutinesApi
    @Test
    fun `Should fill event and attribute ids`() {
        val f = Fixture()
        val event = Event(attributes = (0 until 5).map { Attribute(value = it.toString()) })
        expectCacheLookups(event, f.cache, f.feed)
        val withIds = f.feed.ensureIds(event)
        assertThat(withIds.id, equalTo("5"))
        assertThat(withIds.uuid, !isNullOrBlank)
        withIds.attributes.forEachIndexed { i, attr ->
            assertThat(attr.id, equalTo((5L + i).toString()))
            assertThat(attr.uuid, !isNullOrBlank)
            assertThat(withIds.id, equalTo("5"))
        }
    }

    @ObsoleteCoroutinesApi
    @Test
    fun `Should send event and attributes`() {
        val f = Fixture()
        every { f.cache.getEventId(f.feed.name, any()) } returns Random.nextLong()
        every { f.cache.getAttributeId(f.feed.name, any(), any()) } returns Random.nextLong()
        every { f.cache.attributeHasSent(f.feed.name, any(), any()) } returns false

        val eventCount = 5
        val attributeCount = 5
        val events = (0 until eventCount).map { i ->
            Event(attributes = (0 until attributeCount).map { j -> Attribute(value = (i + j).toString()) })
        }

        f.feed.setEvents(events)
        runBlocking {
            val eventUpdates = f.feed.run().toList()
            assertThat(eventUpdates.size, equalTo(eventCount))
            eventUpdates.forEach { update ->
                assertThat(update.event.uuid, !isNullOrBlank)
                assertThat(update.event.id, !isNullOrBlank)
                update.newAttributes.forEach {
                    assertThat(it.eventId, equalTo(update.event.id))
                    assertThat(it.uuid, !isNullOrBlank)
                    assertThat(it.id, !isNullOrBlank)
                }
            }
        }
    }

    @ObsoleteCoroutinesApi
    @Test
    fun `Should not send attributes that have already been sent`() {
        val f = Fixture()
        every { f.cache.getEventId(f.feed.name, any()) } returns Random.nextLong()
        every { f.cache.getAttributeId(f.feed.name, any(), any()) } returns Random.nextLong()
        every { f.cache.attributeHasSent(f.feed.name, any(), any()) } returns false

        val eventCount = 5
        val attributeCount = 10
        val events = (0 until eventCount).map { i ->
            Event(
                id = UUID.randomUUID().toString(),
                attributes = (0 until attributeCount).map { j ->
                    Attribute(
                        uuid = UUID.randomUUID().toString(),
                        value = (i + j).toString()
                    )
                }
            )
        }

        val duplicateEvent = events[0]
        duplicateEvent.attributes.forEach { attr ->
            every { f.cache.attributeHasSent(f.feed.name, duplicateEvent.id!!, attr.uuid!!) } returns true
        }

        val updatedEvent = events[1]
        updatedEvent.attributes.subList(0, 3).forEach { attr ->
            every { f.cache.attributeHasSent(f.feed.name, updatedEvent.id!!, attr.uuid!!) } returns true
        }

        f.feed.setEvents(events)

        runBlocking {
            val attributesByEventId =
                f.feed.run().toList().map { update -> update.event.id to update.newAttributes }.toMap()
            assertThat(attributesByEventId.size, equalTo(eventCount - 1))
            assertThat(attributesByEventId.containsKey(duplicateEvent.id), equalTo(false))
            assertThat(attributesByEventId[updatedEvent.id]!!.size, equalTo(attributeCount - 3))
        }
    }

    @ObsoleteCoroutinesApi
    @Test
    @ExperimentalCoroutinesApi
    fun `Should propagate exception`() {
        val f = Fixture()
        val badFeed = object : MockFeed(f.cache) {
            override suspend fun pull(): Flow<Event> {
                throw FeedException("BAD", SocketException("ALSO BAD"))
            }
        }
        val exception = assertThrows<FeedException> {
            runBlocking { badFeed.run() }
        }

        assertThat(exception.message, equalTo("BAD"))
        assertThat(exception.cause!!, isA<SocketException>())
        assertThat(exception.cause!!.message, equalTo("ALSO BAD"))
    }
}
