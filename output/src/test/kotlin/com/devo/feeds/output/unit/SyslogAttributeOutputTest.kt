package com.devo.feeds.output.unit

import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.output.EventUpdate
import com.devo.feeds.output.SyslogAttributeOutput
import com.devo.feeds.storage.AttributeCache
import com.devo.feeds.storage.InMemoryAttributeCache
import com.devo.feeds.testutils.TestSyslogServer
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Before
import org.junit.Test

class SyslogAttributeOutputTest {

    private lateinit var attributeCache: AttributeCache
    private lateinit var server: TestSyslogServer
    private lateinit var output: SyslogAttributeOutput

    @ObsoleteCoroutinesApi
    @Before
    fun setUp() {
        server = TestSyslogServer()
        attributeCache = InMemoryAttributeCache().build()
        server.start()
        output = SyslogAttributeOutput().also {
            it.build(
                ConfigFactory.parseMap(
                    mapOf(
                        "host" to "localhost",
                        "port" to server.port,
                        "chain" to server.writeRootCA(),
                        "keystore" to server.writeKeystore(),
                        "tags" to listOf("tag.one", "tag.two", "tag.three"),
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

        await().until { server.receivedMessages.size == 3 }
        val byTag = server.receivedMessages.groupBy {
            val message = it.message
            val bodyStart = message.indexOf('{')
            val header = message.substring(0, bodyStart).trim()
            header.substring(header.indexOfLast { c -> c == ' ' }, header.length)
        }
        assertThat(byTag.size, equalTo(3))
        for (messages in byTag.values) {
            assertThat(messages.size, equalTo(1))
        }
    }
}
