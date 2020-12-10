package com.devo.feeds.testutils

import com.devo.feeds.data.X509Credentials
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.net.SocketException
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

class TestSyslogServer(val port: Int = NetworkUtils.findAvailablePort()) {

    data class ReceivedMessage(val client: String, val message: String)

    val receivedMessages: MutableList<ReceivedMessage> = Collections.synchronizedList(mutableListOf<ReceivedMessage>())

    private val log = KotlinLogging.logger { }
    private val clientThreads = mutableListOf<ReadThread>()
    private var isRunning = false
    private lateinit var serverSocket: SSLServerSocket

    @Suppress("BlockingMethodInNonBlockingContext")
    fun start() = GlobalScope.launch {
        val sc = createSSLContext()
        serverSocket = (sc.serverSocketFactory.createServerSocket(port) as SSLServerSocket).apply {
            needClientAuth = true
        }
        log.info { "Test server listening on 0.0.0.0:$port" }
        isRunning = true
        try {
            while (true) {
                val socket = serverSocket.accept() as SSLSocket
                val client = ReadThread(socket, receivedMessages)
                client.start()
                clientThreads.add(client)
            }
        } catch (se: SocketException) {
            log.info { "Test server shut down" }
            clientThreads.forEach { it.close() }
        }
    }

    private fun createSSLContext(): SSLContext {
        val loader = javaClass.classLoader
        val credentials = X509Credentials(
            loader.getResourceAsStream("mock-server.p12")!!, "changeit",
            mapOf("root" to loader.getResourceAsStream("rootCA.crt")!!)
        )
        return credentials.sslContext
    }

    fun stop() {
        log.info { "Stopping test server" }
        serverSocket.close()
    }

    class ReadThread(
        private val socket: SSLSocket,
        private val messages: MutableList<ReceivedMessage>
    ) : Thread() {

        private val log = KotlinLogging.logger { }

        override fun run() {
            val subject = (socket.session.peerCertificates[0] as X509Certificate).subjectDN.name
            val reader = socket.inputStream.bufferedReader()
            try {
                while (true) {
                    reader.readLine()?.let {
                        log.debug { "Received $it" }
                        messages.add(ReceivedMessage(subject, it))
                    }
                }
            } catch (se: SSLException) {
                // ignore
            }
        }

        fun close() {
            socket.close()
        }
    }
}
