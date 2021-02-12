package com.devo.feeds

import com.devo.feeds.testutils.MispFeedServer
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.typesafe.config.ConfigFactory
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class FeedsServiceIntegrationTest {
    private val server = MispFeedServer().also {
        it.start()
    }

    @AfterAll
    fun tearDown() {
        server.stop()
    }

    class Fixture(mispPort: Int) {
        val service = FeedsService(
            ConfigFactory
                .parseMap(
                    mapOf(
                        "feeds.misp.url" to "http://localhost:$mispPort",
                        "feeds.misp.key" to ""
                    )
                ).withFallback(ConfigFactory.load())
        )
    }

    @KtorExperimentalAPI
    @Test
    fun `Should retrieve configured feeds`() {
        val f = Fixture(server.port)
        runBlocking {
            val feedsAndTags = f.service.getConfiguredFeeds()
            assertThat(feedsAndTags.size, equalTo(3))
            feedsAndTags.withIndex().forEach { (i, value) ->
                assertThat(value.feed.id, equalTo(i.toString()))
                assertThat(value.feed.name, equalTo(i.toString()))
            }
        }
    }
}
