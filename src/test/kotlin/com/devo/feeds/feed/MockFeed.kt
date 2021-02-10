package com.devo.feeds.feed

import com.devo.feeds.data.misp.Event
import com.devo.feeds.storage.AttributeCache
import java.time.Duration
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

@ObsoleteCoroutinesApi
open class MockFeed(attributeCache: AttributeCache) :
    Feed(FeedSpec("mock", Duration.ofSeconds(30), "https://localhost", null, attributeCache)) {

    private var toReturn = emptyList<Event>()

    override suspend fun pull(): Flow<Event> = toReturn.asFlow()

    fun setEvents(events: List<Event>) {
        toReturn = events
    }
}
