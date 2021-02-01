package com.devo.feeds.output

import com.cloudbees.syslog.sender.TcpSyslogMessageSender
import com.devo.feeds.data.X509Credentials
import com.typesafe.config.Config
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

class SyslogOutputFactory : OutputFactory<SyslogOutput> {
    private val log = KotlinLogging.logger { }
    override fun fromConfig(config: Config): SyslogOutput {
        val host = config.getString("host")
        val port = config.getInt("port")
        val tags = config.getStringList("tags")
        val threads = config.getIntOrDefault("threads", 1)
        val credentials = if (config.hasPath("keystore") && config.hasPath("keystorePass")) {
            log.info { "Using TLS" }
            val trustedCerts = if (config.hasPath("chain")) {
                mapOf("chain" to config.getString("chain"))
            } else emptyMap()
            X509Credentials(config.getString("keystore"), config.getString("keystorePass"), trustedCerts)
        } else {
            log.info { "Using plaintext" }
            null
        }
        return SyslogOutput(host, port, tags, credentials, threads)
    }
}

open class SyslogOutput(
    private val host: String,
    private val port: Int,
    private val tags: List<String>,
    private val credentials: X509Credentials?,
    threads: Int
) : Output {

    private val log = KotlinLogging.logger { }

    @ObsoleteCoroutinesApi
    private val writeContext = newFixedThreadPoolContext(threads, "syslog-write-context")

    @ObsoleteCoroutinesApi
    private val writeJobs: List<Job> = (0 until threads).map {
        GlobalScope.launch { launchWriter(it) }
    }
    private val messageChannel = Channel<Pair<String, String>>()

    private fun TcpSyslogMessageSender.sendMessageWithTag(tag: String, message: String) {
        this.defaultAppName = tag
        sendMessage(message)
        this.defaultAppName = null
    }

    protected open fun tagsForFeed(feed: String): Iterable<String> = tags

    protected suspend fun submit(feed: String, message: String) = messageChannel.send(feed to message)

    @ObsoleteCoroutinesApi
    private fun CoroutineScope.launchWriter(id: Int) = launch(writeContext) {
        log.info { "Connecting to syslog at $host:$port with writer $id" }
        TcpSyslogMessageSender().apply {
            syslogServerHostname = host
            syslogServerPort = port
            credentials?.let {
                isSsl = true
                sslContext = it.sslContext
            }
        }.use { sender ->
            for ((feed, msg) in messageChannel) {
                for (tag in tagsForFeed(feed)) {
                    sender.sendMessageWithTag(tag, msg)
                }
            }
        }
    }

    override suspend fun write(feed: String, eventUpdate: EventUpdate) {
        log.info { "Writing feed: $feed, event: ${eventUpdate.event.uuid}" }
        submit(feed, Json.encodeToString(eventUpdate))
    }

    @ObsoleteCoroutinesApi
    override fun close() {
        runBlocking {
            writeJobs.forEach { it.cancelAndJoin() }
        }
    }
}
