package com.devo.feeds.output

import com.typesafe.config.Config

interface OutputFactory<T : Output> {
    fun fromConfig(config: Config): T
}
