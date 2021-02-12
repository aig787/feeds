package com.devo.feeds.feed

import com.devo.feeds.storage.AttributeCache
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.ktor.util.KtorExperimentalAPI
import io.mockk.mockk
import java.time.Duration
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.jupiter.api.Test

class MispFeedTest {

    class Fixture {
        @KtorExperimentalAPI
        @ObsoleteCoroutinesApi
        val feed = MispFeed(
            FeedSpec(
                "test",
                Duration.ofSeconds(30),
                "https://www.circl.lu/doc/misp/feed-osint",
                null,
                mockk<AttributeCache>()
            ),
        )
    }

    @KtorExperimentalAPI
    @ObsoleteCoroutinesApi
    @Test
    fun `Should parse MISP manifest`() {
        val f = Fixture()
        val manifestString =
            javaClass.classLoader.getResourceAsStream("manifest.json")!!.readBytes().decodeToString()
        val manifestEvents = f.feed.parseManifest(manifestString)
        assertThat(manifestEvents.size, equalTo(1208))
        val event = manifestEvents["57cebd3b-3b9c-4f70-95ff-414d950d210f"]
        assertThat(event?.timestamp, equalTo("1473241492"))
        assertThat(event?.tags?.size, equalTo(3))
    }

    @KtorExperimentalAPI
    @ObsoleteCoroutinesApi
    @Test
    fun `Should parse MISP event`() {
        val f = Fixture()
        val eventString = javaClass.classLoader.getResourceAsStream("event.json")!!.readBytes().decodeToString()
        val event = f.feed.parseEvent(eventString)
        assertThat(event?.tags?.size, equalTo(5))
        assertThat(event?.uuid, equalTo("59a3d08d-5dc8-4153-bc7c-456d950d210f"))
    }
}
