package com.devo.feeds.output

import com.cloudbees.syslog.sender.TcpSyslogMessageSender
import com.devo.feeds.data.X509Credentials
import com.devo.feeds.data.misp.Attribute
import com.devo.feeds.data.misp.Event
import com.devo.feeds.data.misp.EventTag
import com.devo.feeds.data.misp.MispObject
import com.typesafe.config.Config
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

@Serializable
data class DevoMispAttribute(
    @SerialName("Attribute") val attribute: Attribute,
    @SerialName("Event") val event: Event,
    @SerialName("Object") val mispObject: MispObject? = null,
    @SerialName("EventTags") val eventTags: List<EventTag> = emptyList()
)

class DevoAttributeOutput : AttributeOutput {

    private val log = KotlinLogging.logger { }
    private val tag = "threatintel.misp.attributes"
    private val syslogSenders = mutableMapOf<String, TcpSyslogMessageSender>()

    private lateinit var host: String
    private lateinit var credentials: X509Credentials
    private var port by Delegates.notNull<Int>()
    private lateinit var context: CoroutineContext

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
    fun build(
        host: String,
        port: Int,
        credentials: X509Credentials,
        threads: Int
    ): AttributeOutput {
        this.host = host
        this.port = port
        this.credentials = credentials
        context = newFixedThreadPoolContext(threads, "write-threads")
        return this
    }

    private fun getSender(thread: String): TcpSyslogMessageSender {
        return if (syslogSenders.containsKey(thread)) {
            syslogSenders[thread]!!
        } else {
            log.info { "Connecting to Devo at $host:$port with thread $thread" }
            TcpSyslogMessageSender().apply {
                syslogServerHostname = host
                syslogServerPort = port
                defaultAppName = tag
                isSsl = true
                sslContext = credentials.sslContext
            }.also {
                syslogSenders[thread] = it
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun serializeDevoAttribute(attribute: DevoMispAttribute): String {
        log.debug { "Serializing event:${attribute.event.uuid} attribute:${attribute.attribute.uuid}" }
        return Json.encodeToString(attribute)
    }

    @ObsoleteCoroutinesApi
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun sendMessage(message: String) = withContext(context) {
        launch {
            val thread = Thread.currentThread().name
            log.debug { "Sending ${message.length} bytes with $thread" }
            getSender(thread).sendMessage(message)
            log.debug { "Finished sending ${message.length} bytes" }
        }
    }

    private fun getDevoAttributesFromEvent(
        eventUpdate: EventUpdate
    ): Flow<DevoMispAttribute> = eventUpdate.newAttributes.asFlow().map { attr ->
        DevoMispAttribute(
            attribute = attr,
            event = eventUpdate.event,
            eventTags = eventUpdate.event.eventTag
        )
    }.onEach {
        log.trace { "Created Devo Attribute for event: ${it.event.uuid}, attribute:${it.attribute.uuid}" }
    }

    @InternalCoroutinesApi
    @ObsoleteCoroutinesApi
    override suspend fun write(feed: String, eventUpdate: EventUpdate) = coroutineScope {
        getDevoAttributesFromEvent(eventUpdate).map {
            async {
                log.info { "Writing feed: $feed, event: ${it.event.uuid}, attribute: ${it.attribute.uuid}" }
                serializeDevoAttribute(it)
            }
        }.map { sendMessage(it.await()) }.collect { it.join() }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        syslogSenders.forEach { (_, sender) ->
            try {
                sender.close()
            } catch (npe: NullPointerException) {
                // ignore
            }
        }
    }
}
