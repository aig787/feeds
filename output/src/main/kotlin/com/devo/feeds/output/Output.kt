package com.devo.feeds.output

interface Output {
    suspend fun write(feed: String, eventUpdate: EventUpdate)
    fun close()

    class WriteException(message: String, cause: Throwable?) : RuntimeException(message, cause) {
        constructor(message: String) : this(message, null)
    }
}
