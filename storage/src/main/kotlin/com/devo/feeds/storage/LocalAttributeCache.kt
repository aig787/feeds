package com.devo.feeds.storage

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.Paths
import org.mapdb.Atomic
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.HTreeMap
import org.mapdb.Serializer

abstract class LocalAttributeCache : AttributeCache {
    private lateinit var db: DB
    private lateinit var sentAttributes: HTreeMap.KeySet<String>
    private lateinit var eventIds: HTreeMap<String, Long>
    private lateinit var attributeIds: HTreeMap<String, Long>
    private lateinit var attributeCounter: Atomic.Long
    private lateinit var eventCounter: Atomic.Long

    abstract fun getDB(config: Config): DB

    override fun build(config: Config): AttributeCache {
        db = getDB(config)
        sentAttributes = db.hashSet("sent-attributes")
            .serializer(Serializer.STRING)
            .createOrOpen()
        eventIds = db.hashMap("event-ids")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.LONG)
            .createOrOpen()
        attributeIds = db.hashMap("attribute-ids")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.LONG)
            .createOrOpen()
        attributeCounter = db.atomicLong("attribute-counter")
            .createOrOpen()
        eventCounter = db.atomicLong("event-counter")
            .createOrOpen()
        return this
    }

    private fun attributeKey(feed: String, eventUUID: String, uuid: String): String = "$feed$eventUUID$uuid"
    private fun eventKey(feed: String, uuid: String): String = "$feed$uuid"

    override fun getEventId(feed: String, uuid: String): Long {
        val key = eventKey(feed, uuid)
        eventIds.putIfAbsent(key, eventCounter.incrementAndGet())
        return eventIds[key]!!
    }

    override fun getAttributeId(feed: String, eventId: String, uuid: String): Long {
        val key = attributeKey(feed, eventId, uuid)
        attributeIds.putIfAbsent(key, attributeCounter.incrementAndGet())
        return attributeIds[key]!!
    }

    override fun attributeHasSent(feed: String, eventId: String, uuid: String): Boolean {
        return sentAttributes.contains(attributeKey(feed, eventId, uuid))
    }

    override fun markAttributeSent(feed: String, eventId: String, uuid: String) {
        sentAttributes.add(attributeKey(feed, eventId, uuid))
    }

    override fun close() = db.close()
}

open class FilesystemAttributeCache : LocalAttributeCache() {
    override fun getDB(config: Config): DB {
        val path = Paths.get(config.getString("path")).toFile()
        if (path.isFile) {
            throw FileNotFoundException("$path must be a directory")
        } else if (!path.exists()) {
            path.mkdirs()
        }
        val cachePath = Paths.get(path.toString(), "attribute-cache")
        return DBMaker.fileDB(cachePath.toString()).checksumHeaderBypass().fileMmapEnableIfSupported().make()
    }

    fun build(path: Path): AttributeCache = build(
        ConfigFactory.parseMap(
            mapOf(
                "path" to path.toString()
            )
        )
    )
}

open class InMemoryAttributeCache : LocalAttributeCache() {
    override fun getDB(config: Config): DB = DBMaker.memoryDB().make()
    fun build(): AttributeCache = build(ConfigFactory.empty())
}
