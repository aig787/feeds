package com.devo.feeds.output

import com.typesafe.config.Config

fun Config.getIntOrDefault(path: String, default: Int): Int = if (hasPath(path)) getInt(path) else default
