package com.devo.feeds.output.unit

import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.output.DevoAttributeOutput
import com.devo.feeds.output.DevoMispAttribute
import com.devo.feeds.output.EventUpdate
import com.devo.feeds.storage.AttributeCache
import com.devo.feeds.storage.InMemoryAttributeCache
import com.devo.feeds.testutils.TestSyslogServer
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.endsWith
import com.natpryce.hamkrest.equalTo
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Before
import org.junit.Test

class DevoAttributeOutputTest {

    private lateinit var attributeCache: AttributeCache
    private lateinit var server: TestSyslogServer
    private lateinit var output: DevoAttributeOutput

    @ObsoleteCoroutinesApi
    @Before
    fun setUp() {
        server = TestSyslogServer()
        attributeCache = InMemoryAttributeCache().build()
        server.start()
        output = DevoAttributeOutput().also {
            it.build(
                ConfigFactory.parseMap(
                    mapOf(
                        "host" to "localhost",
                        "port" to server.port,
                        "chain" to server.writeRootCA(),
                        "keystore" to server.writeKeystore(),
                        "keystorePass" to "changeit",
                        "threads" to 2
                    )
                )
            )
        }
    }

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
