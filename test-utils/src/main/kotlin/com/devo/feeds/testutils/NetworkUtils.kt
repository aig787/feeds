package com.devo.feeds.testutils

import java.net.ServerSocket

object NetworkUtils {
    fun findAvailablePort(): Int {
        ServerSocket(0).use {
            it.reuseAddress = true
            return it.localPort
        }
    }
}
