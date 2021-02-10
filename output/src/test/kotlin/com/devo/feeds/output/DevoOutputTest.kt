package com.devo.feeds.output

import com.devo.feeds.data.X509Credentials
import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.storage.AttributeCache
import com.devo.feeds.storage.InMemoryAttributeCache
import com.devo.feeds.testutils.TestSyslogServer
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.endsWith
import com.natpryce.hamkrest.equalTo
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Before
import org.junit.Test

class DevoOutputTest {

    private lateinit var attributeCache: AttributeCache
    private lateinit var server: TestSyslogServer
    private lateinit var output: DevoOutput

    @ObsoleteCoroutinesApi
    @Before
    fun setUp() {
        server = TestSyslogServer()
        attributeCache = InMemoryAttributeCache()
        server.start()
        val credentials = X509Credentials(
            server.keystoreBytes.inputStream(),
            "changeit",
            mapOf("chain" to server.rootCABytes.inputStream())
        )
        output = DevoOutput("localhost", server.port, credentials, 2)
    }

    @ObsoleteCoroutinesApi
    @After
    fun tearDown() {
        attributeCache.close()
        output.close()
        server.stop()
    }

    @InternalCoroutinesApi
    @ObsoleteCoroutinesApi
    @Test
    fun `Should write events to syslog server`() {
        val attributeCount = 20
        val eventId = "0"
        val testEvent = Event(
            id = eventId,
            attributes = (0 until attributeCount).map { Attribute(eventId = eventId, id = it.toString()) }
        )

        runBlocking {
            output.write("feed", EventUpdate(testEvent, testEvent.attributes))
        }

        await().until { server.receivedMessages.size == attributeCount }
        val sorted = server.receivedMessages.map {
            val message = it.message
            val bodyStart = message.indexOf('{')
            val header = message.substring(0, bodyStart).trim()
            assertThat(header, endsWith("threatintel.misp.attributes:"))
            val body = message.substring(bodyStart, message.length).trim()
            Json.decodeFromString<DevoMispAttribute>(body)
        }.sortedBy { it.attribute.id?.toInt() }
        sorted.forEachIndexed { index, devoMispAttribute ->
            assertThat(devoMispAttribute.event, equalTo(testEvent.copy(attributes = emptyList())))
            assertThat(devoMispAttribute.attribute, equalTo(testEvent.attributes[index]))
        }
    }
}
