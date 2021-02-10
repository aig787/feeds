package com.devo.feeds.output

import com.typesafe.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

class KafkaOutputFactory : OutputFactory<KafkaOutput> {
    override fun fromConfig(config: Config): KafkaOutput {
        val eventTopic = config.getString("eventTopic")
        val attributeTopic = config.getString("attributeTopic")
        val threads = config.getIntOrDefault("threads", 1)
        val producerConfig = config.getConfig("properties").entrySet().map { (k, v) ->
            k to v.unwrapped().toString()
        }.toMap()
        val producer = KafkaProducer(producerConfig, StringSerializer(), StringSerializer())
        return KafkaOutput(eventTopic, attributeTopic, threads) { producer }
    }
}

class KafkaOutput(
    private val eventTopic: String,
    private val attributeTopic: String,
    val threads: Int,
    private val producerFactory: () -> Producer<String, String>
) : Output {

    private val log = KotlinLogging.logger { }
    private val messageChannel = Channel<Pair<String, String>>()
    private val producerJobs: List<Job> = (0 until threads).map {
        CoroutineScope(Dispatchers.IO).launch {
            launchProducer(it)
        }
    }

    private suspend fun launchProducer(id: Int) = withContext(Dispatchers.IO) {
        log.info { "Initializing producer $id" }
        launch {
            producerFactory().use { producer ->
                for ((topic, msg) in messageChannel) {
                    log.trace { "Producing $topic - $msg" }
                    producer.send(ProducerRecord(topic, msg))
                }
            }
        }
    }

    override suspend fun write(feed: String, eventUpdate: EventUpdate) {
        log.info { "Writing event and ${eventUpdate.newAttributes.size} attributes for $feed" }
        messageChannel.send(eventTopic to Json.encodeToString(eventUpdate.event))
        eventUpdate.newAttributes.forEach {
            messageChannel.send(attributeTopic to Json.encodeToString(it))
        }
    }

    override fun close() {
        runBlocking {
            producerJobs.forEach { it.cancelAndJoin() }
        }
    }
}
