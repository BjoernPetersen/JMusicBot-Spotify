package net.bjoernpetersen.spotify

import net.bjoernpetersen.musicbot.spi.config.ConfigChecker

internal fun <T> nullConfigChecker(): ConfigChecker<T> = { if (it == null) "Required" else null }
