package com.devo.feeds.integration

import com.devo.feeds.FeedsService
import com.devo.feeds.testutils.MispFeedServer
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.typesafe.config.ConfigFactory
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test

class FeedsServiceIntegrationTest {
    companion object {
        private val server = MispFeedServer().also {
            it.start()
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            server.stop()
        }
    }

    private lateinit var service: FeedsService

    @Before
    fun setUp() {
        val config = ConfigFactory
            .parseMap(
                mapOf(
                    "feeds.misp.url" to "http://localhost:${server.port}",
                    "feeds.misp.key" to ""
                )
            ).withFallback(ConfigFactory.load())
        service = FeedsService(config)
    }

    @KtorExperimentalAPI
    @Test
    fun `Should retrieve configured feeds`() {
        runBlocking {
            val feedsAndTags = service.getConfiguredFeeds()
            assertThat(feedsAndTags.size, equalTo(3))
            feedsAndTags.withIndex().forEach { (i, value) ->
                assertThat(value.feed.id, equalTo(i.toString()))
                assertThat(value.feed.name, equalTo(i.toString()))
            }
        }
    }
}
