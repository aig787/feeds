package com.devo.feeds.storage

import com.typesafe.config.Config

interface AttributeCacheFactory<T : AttributeCache> {
    fun fromConfig(config: Config): T
}
