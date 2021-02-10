package com.devo.feeds.output

import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class KafkaOutputIntegrationTest {

    private val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3")).also { it.start() }

    @Test
    fun `Should write to kafka`() {
        val testId = UUID.randomUUID().toString()
        val eventTopic = "$testId-events"
        val attributeTopic = "$testId-attributes"
        val output = KafkaOutput(eventTopic, attributeTopic, 2) {
            KafkaProducer(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers
                ), StringSerializer(), StringSerializer()
            )
        }
        val eventCount = 50
        val attributeCount = 25
        val updates = (0 until eventCount).map { eventId ->
            val attributes =
                (0 until attributeCount).map { Attribute(id = "$eventId-$it", eventId = eventId.toString()) }
            EventUpdate(Event(id = eventId.toString()), attributes)
        }
        GlobalScope.launch {
            updates.forEach {
                output.write("test-feed", it)
            }
        }

        val consumer = KafkaConsumer(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to testId,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
            ),
            StringDeserializer(),
            StringDeserializer()
        )
        consumer.subscribe(listOf(eventTopic, attributeTopic))
        val events = mutableListOf<String>()
        val attributes = mutableListOf<String>()
        await().until {
            val records = consumer.poll(Duration.ofSeconds(1))
            events.addAll(records.records(eventTopic).map { it.value() })
            attributes.addAll(records.records(attributeTopic).map { it.value() })
            events.size == eventCount && attributes.size == eventCount * attributeCount
        }
        assertThat(events.toSet(), equalTo(updates.map { Json.encodeToString(it.event) }.toSet()))
        assertThat(
            attributes.toSet(),
            equalTo(updates.flatMap { it.newAttributes.map { Json.encodeToString(it) } }.toSet())
        )
    }
}
