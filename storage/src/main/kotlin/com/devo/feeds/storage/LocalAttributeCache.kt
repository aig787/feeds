package com.devo.feeds.storage

import com.typesafe.config.Config
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Paths
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer

abstract class LocalAttributeCache(private val db: DB) : AttributeCache {
    private val sentAttributes = db.hashSet("sent-attributes")
        .serializer(Serializer.STRING)
        .createOrOpen()
    private val eventIds = db.hashMap("event-ids")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.LONG)
        .createOrOpen()
    private val attributeIds = db.hashMap("attribute-ids")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.LONG)
        .createOrOpen()
    private val attributeCounter = db.atomicLong("attribute-counter")
        .createOrOpen()
    private val eventCounter = db.atomicLong("event-counter")
        .createOrOpen()

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

class FilesystemAttributeCacheFactory : AttributeCacheFactory<FilesystemAttributeCache> {
    override fun fromConfig(config: Config): FilesystemAttributeCache {
        val path = Paths.get(config.getString("path")).toFile()
        if (path.isFile) {
            throw FileNotFoundException("$path must be a directory")
        } else if (!path.exists()) {
            path.mkdirs()
        }
        val cachePath = Paths.get(path.toString(), "attribute-cache")
        return FilesystemAttributeCache(cachePath.toFile())
    }
}

class InMemoryAttributeCacheFactory : AttributeCacheFactory<InMemoryAttributeCache> {
    override fun fromConfig(config: Config): InMemoryAttributeCache = InMemoryAttributeCache()
}

open class FilesystemAttributeCache(file: File) :
    LocalAttributeCache(DBMaker.fileDB(file).checksumHeaderBypass().fileMmapEnableIfSupported().make())

open class InMemoryAttributeCache : LocalAttributeCache(DBMaker.memoryDB().make())
