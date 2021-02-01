package com.devo.feeds.storage

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.streams.asStream
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource

class AttributeCacheTest {
    class AttributeCacheArgumentsProvider : ArgumentsProvider {
        val x = Paths.get(Files.createTempDirectory("feeds-test").toString(), "")
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> =
            sequenceOf(
                Arguments.of(
                    FilesystemAttributeCache(
                        Paths.get(Files.createTempDirectory("feeds-test").toString(), "cache").toFile()
                    )
                ),
                Arguments.of(InMemoryAttributeCache())
            ).asStream()
    }

    @ParameterizedTest
    @ArgumentsSource(AttributeCacheArgumentsProvider::class)
    fun `Should increment eventId`(cache: AttributeCache) {
        assertThat(cache.getEventId("test", "test1"), equalTo(1L))
        assertThat(cache.getEventId("test", "test2"), equalTo(2L))
        assertThat(cache.getEventId("test", "test1"), equalTo(1L))
    }

    @ParameterizedTest
    @ArgumentsSource(AttributeCacheArgumentsProvider::class)
    fun `Should increment attributeId`(cache: AttributeCache) {
        assertThat(cache.getAttributeId("test", "test", "test1"), equalTo(1L))
        assertThat(cache.getAttributeId("test", "test", "test2"), equalTo(2L))
        assertThat(cache.getAttributeId("test", "test", "test1"), equalTo(1L))
    }

    @ParameterizedTest
    @ArgumentsSource(AttributeCacheArgumentsProvider::class)
    fun `Should check and mark attribute sent`(cache: AttributeCache) {
        assertThat(cache.attributeHasSent("test", "test", "test"), equalTo(false))
        cache.markAttributeSent("test", "test", "test")
        assertThat(cache.attributeHasSent("test", "test", "test"), equalTo(true))
    }
}
