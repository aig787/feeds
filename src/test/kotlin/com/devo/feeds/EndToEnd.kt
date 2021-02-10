package com.devo.feeds

import com.devo.feeds.data.misp.FeedAndTag
import com.devo.feeds.data.misp.FeedConfig
import com.devo.feeds.data.misp.Tag
import com.devo.feeds.output.DevoMispAttribute
import com.devo.feeds.output.DevoOutputFactory
import com.devo.feeds.output.KafkaOutputFactory
import com.devo.feeds.storage.InMemoryAttributeCacheFactory
import com.devo.feeds.testutils.MispFeedServer
import com.devo.feeds.testutils.TestSyslogServer
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.typesafe.config.ConfigFactory
import io.ktor.util.KtorExperimentalAPI
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.Tag("integration")
class EndToEnd {

    private val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3")).also { it.start() }

    class Fixture(bootstrap: String) {
        val mispServer = MispFeedServer().also { it.start() }
        val outputServer = TestSyslogServer()
        val id = UUID.randomUUID().toString()
        val eventTopic = "$id-event"
        val attributeTopic = "$id-attribute"
        private val config = ConfigFactory.parseMap(
            mapOf(
                "feeds.mispUpdateInterval" to "1 second",
                "feeds.misp.url" to "http://localhost:${mispServer.port}",
                "feeds.misp.key" to "",
                "feeds.cache" to mapOf(
                    "class" to InMemoryAttributeCacheFactory::class.qualifiedName,
                    "path" to Files.createTempFile("feeds-e2e", UUID.randomUUID().toString()).toString()
                ),
                "feeds.outputs" to listOf(
                    mapOf(
                        "class" to DevoOutputFactory::class.qualifiedName,
                        "host" to "localhost",
                        "port" to outputServer.port,
                        "chain" to resourcePath("rootCA.crt"),
                        "keystore" to resourcePath("clienta.p12"),
                        "keystorePass" to "changeit",
                        "threads" to 1
                    ),
                    mapOf(
                        "class" to KafkaOutputFactory::class.qualifiedName,
                        "eventTopic" to eventTopic,
                        "attributeTopic" to attributeTopic,
                        "properties" to mapOf(
                            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap
                        )
                    )
                )
            )
        ).withFallback(ConfigFactory.load())

        private val service = FeedsService(config)

        private lateinit var outputServerJob: Job
        private lateinit var serviceJob: Job

        private fun resourcePath(resource: String): String =
            javaClass.classLoader.getResource(resource)!!.path

        @KtorExperimentalAPI
        @ObsoleteCoroutinesApi
        @FlowPreview
        fun start() {
            outputServerJob = outputServer.start()
            serviceJob = GlobalScope.launch {
                service.run()
            }
        }

        fun stop() {
            service.stop()
            runBlocking { serviceJob.cancelAndJoin() }
            outputServer.stop()
            runBlocking { outputServerJob.cancelAndJoin() }
            mispServer.stop()
        }
    }

    private fun assertKafkaContents(f: Fixture) = KafkaConsumer(
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.GROUP_ID_CONFIG to f.id
        ), StringDeserializer(), StringDeserializer()
    ).use { consumer ->
        consumer.subscribe(listOf(f.eventTopic, f.attributeTopic))
        val expectedEvents = (f.mispServer.feedCount + 1) * f.mispServer.manifestEvents
        val expectedAttributes = expectedEvents * f.mispServer.attributesPerEvent
        val events = mutableListOf<String>()
        val attributes = mutableListOf<String>()
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val records = consumer.poll(Duration.ofSeconds(1))
            println(records.count())
            events.addAll(records.records(f.eventTopic).map { it.value() })
            attributes.addAll(records.records(f.attributeTopic).map { it.value() })
            assertThat(events.size, equalTo(expectedEvents))
            assertThat(attributes.size, equalTo(expectedAttributes))
        }
    }

    private fun assertDevoContents(f: Fixture) {
        val expectedAttributeCount =
            f.mispServer.feedCount * f.mispServer.attributesPerEvent * f.mispServer.manifestEvents
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(f.outputServer.receivedMessages.size, equalTo(expectedAttributeCount))
        }
        val byEventId = f.outputServer.receivedMessages.map { (_, message) ->
            val bodyStart = message.indexOf('{')
            Json.decodeFromString<DevoMispAttribute>(message.substring(bodyStart, message.length))
        }.groupBy { it.event.uuid!! }
        assertThat(byEventId.size, equalTo(f.mispServer.feedCount * f.mispServer.manifestEvents))
        byEventId.forEach { (id, attributes) ->
            val eventTag = Tag(id = id)
            val feedTag = Tag(id = id.split("-").first())
            assertThat(attributes.size, equalTo(f.mispServer.attributesPerEvent))
            attributes.forEach { attr ->
                assertThat(attr.eventTags, equalTo(setOf(feedTag, eventTag)))
                assertThat(attr.event.tags, equalTo(setOf(feedTag, eventTag)))
                assertThat(attr.attribute.tags, equalTo(setOf(feedTag, eventTag, Tag(id = attr.attribute.id))))
            }
        }
    }

    @ObsoleteCoroutinesApi
    @FlowPreview
    @KtorExperimentalAPI
    @InternalCoroutinesApi
    @Test
    fun `Should run successfully end to end`() {
        val f = Fixture(kafka.bootstrapServers)
        f.start()

        // Assert all events come through
        assertDevoContents(f)

        // Change one feed and add a new one
        val firstFeed = f.mispServer.feeds.first()
        f.mispServer.feeds = listOf(firstFeed.copy(feed = firstFeed.feed.copy(provider = "updated")))
            .plus(f.mispServer.feeds.subList(1, f.mispServer.feeds.size))
            .plus(
                FeedAndTag(
                    FeedConfig(
                        id = "new",
                        name = "new",
                        provider = "new",
                        url = "http://localhost:${f.mispServer.port}/new",
                        enabled = true,
                        sourceFormat = "misp"
                    )
                )
            )

        val expectedAfterNewFeed =
            (f.mispServer.feedCount + 1) * f.mispServer.attributesPerEvent * f.mispServer.manifestEvents
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(f.outputServer.receivedMessages.size, equalTo(expectedAfterNewFeed))
        }

        assertKafkaContents(f)
        f.stop()
    }
}
