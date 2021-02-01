package com.devo.feeds.testutils

import com.devo.feeds.data.X509Credentials
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.file.Files
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

class TestSyslogServer(val port: Int = NetworkUtils.findAvailablePort(), val tls: Boolean = true) {

    data class ReceivedMessage(val client: String, val message: String)

    val receivedMessages: MutableList<ReceivedMessage> = Collections.synchronizedList(mutableListOf<ReceivedMessage>())

    private val log = KotlinLogging.logger { }
    private val clientThreads = mutableListOf<ReadThread>()
    private var isRunning = false
    private lateinit var serverSocket: ServerSocket

    val keystoreBytes = javaClass.classLoader.getResourceAsStream("clienta.p12")!!.readAllBytes()
    val rootCABytes = javaClass.classLoader.getResourceAsStream("rootCA.crt")!!.readAllBytes()

    @Suppress("BlockingMethodInNonBlockingContext")
    fun start() = GlobalScope.launch {
        serverSocket = if (tls) {
            val sc = createSSLContext()
            (sc.serverSocketFactory.createServerSocket(port) as SSLServerSocket).apply {
                needClientAuth = true
            }
        } else {
            ServerSocket(port)
        }
        log.info { "Test server listening on 0.0.0.0:$port" }
        isRunning = true
        try {
            while (true) {
                val socket = serverSocket.accept()
                val client = ReadThread(socket, receivedMessages)
                client.start()
                clientThreads.add(client)
            }
        } catch (se: SocketException) {
            if (se.message?.equals("Socket closed") == true) {
                log.info { "Test server shut down" }
            } else {
                log.error(se) { "Test server shut down" }
            }
            clientThreads.forEach { it.close() }
        }
    }

    private fun createSSLContext(): SSLContext {
        val credentials = X509Credentials(
            javaClass.classLoader.getResourceAsStream("mock-server.p12")!!,
            "changeit",
            mapOf("root" to ByteArrayInputStream(rootCABytes))
        )
        return credentials.sslContext
    }

    fun stop() {
        log.info { "Stopping test server" }
        serverSocket.close()
    }

    fun writeKeystore(): String = Files.createTempFile("test-syslog", "keystore").also {
        Files.write(it, keystoreBytes)
    }.toString()

    fun writeRootCA(): String = Files.createTempFile("test-syslog", "rootCA").also {
        Files.write(it, rootCABytes)
    }.toString()

    class ReadThread(
        private
        val socket: Socket,
        private val messages: MutableList<ReceivedMessage>
    ) : Thread() {

        private val log = KotlinLogging.logger { }

        override fun run() {
            val client = when (socket) {
                is SSLSocket -> (socket.session.peerCertificates[0] as X509Certificate).subjectDN.name
                else -> socket.inetAddress.toString()
            }
            val reader = socket.inputStream.bufferedReader()
            try {
                while (true) {
                    reader.readLine()?.let {
                        log.debug { "Received $it" }
                        messages.add(ReceivedMessage(client, it))
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
