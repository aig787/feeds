package com.devo.feeds.feed

import com.devo.feeds.data.misp.ManifestEvent
import com.devo.feeds.testutils.MispFeedServer
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.ktor.util.KtorExperimentalAPI
import io.mockk.mockk
import java.time.Duration
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class MispFeedIntegrationTest {
    private val server = MispFeedServer().also {
        it.start()
    }

    @AfterAll
    fun tearDown() {
        server.stop()
    }

    class Fixture(mispPort: Int) {
        @KtorExperimentalAPI
        @ObsoleteCoroutinesApi
        val feed = MispFeed(
            FeedSpec(
                "test",
                Duration.ofSeconds(30),
                "http://localhost:$mispPort/0",
                null,
                mockk()
            ),
        )
    }

    @KtorExperimentalAPI
    @ObsoleteCoroutinesApi
    @Test
    fun `Should fetch MISP manifest`() {
        val f = Fixture(server.port)
        runBlocking {
            val manifest = f.feed.fetchManifest()
            assertThat(
                Json.decodeFromString<Map<String, ManifestEvent>>(manifest),
                equalTo(server.manifest)
            )
        }
    }

    @InternalCoroutinesApi
    @ObsoleteCoroutinesApi
    @KtorExperimentalAPI
    @Test
    fun `Should fetch MISP events`() {
        val f = Fixture(server.port)
        runBlocking {
            val events = f.feed.fetchEvents(setOf("event1", "event2", "event3")).toList()
            assertThat(events.size, equalTo(3))
        }
    }
}
