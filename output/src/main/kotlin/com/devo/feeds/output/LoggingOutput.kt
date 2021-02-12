package com.devo.feeds.output

import com.typesafe.config.Config
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

class LoggingOutputFactory : OutputFactory<LoggingOutput> {
    override fun fromConfig(config: Config): LoggingOutput = LoggingOutput()
}

open class LoggingOutput : Output {

    private val log = KotlinLogging.logger { }

    override suspend fun write(feed: String, eventUpdate: EventUpdate) {
        log.info { "feed: $feed, event: ${Json.encodeToString(eventUpdate)}" }
    }

    override fun close() {
        // noop
    }
}
