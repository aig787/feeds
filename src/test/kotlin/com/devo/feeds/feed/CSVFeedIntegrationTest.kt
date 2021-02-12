package com.devo.feeds.feed

import com.devo.feeds.testutils.MispFeedServer
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.greaterThan
import io.ktor.util.KtorExperimentalAPI
import io.mockk.mockk
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class CSVFeedIntegrationTest {
    private val server = MispFeedServer().also {
        it.start()
    }

    @AfterAll
    fun tearDown() {
        server.stop()
    }

    class Fixture(mispPort: Int) {
        val eventId = UUID.randomUUID().toString()

        @KtorExperimentalAPI
        @ObsoleteCoroutinesApi
        val feed = CSVFeed(
            FeedSpec(
                "test",
                Duration.ofSeconds(30),
                "http://localhost:$mispPort/attributes.csv",
                null,
                mockk()
            ),
            null,
            eventId,
        )
    }

    @KtorExperimentalAPI
    @ObsoleteCoroutinesApi
    @Test
    fun `Should fetch attributes`() {
        val f = Fixture(server.port)
        runBlocking {
            val attrs = f.feed.pull().toList()
            assertThat(attrs.size, equalTo(1))
            val event = attrs[0]
            assertThat(event.id, equalTo(f.eventId))
            assertThat(event.attributes.size, greaterThan(0))
        }
    }
}
