package com.github.bjoernpetersen.spotify

import com.github.bjoernpetersen.musicbot.spi.plugin.Plugin
import com.github.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import mu.KotlinLogging

internal object LogInitStateWriter : InitStateWriter {
    private val logger = KotlinLogging.logger {}
    private var current: Plugin? = null

    override fun begin(plugin: Plugin) {
        current = plugin
    }

    private fun prefix(message: String): String {
        return current?.name?.let { "$it: $message" } ?: message
    }

    override fun state(state: String) {
        logger.info { prefix(state) }
    }

    override fun warning(warning: String) {
        logger.warn { prefix(warning) }
    }
}
