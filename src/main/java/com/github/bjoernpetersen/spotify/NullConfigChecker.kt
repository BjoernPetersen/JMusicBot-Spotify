package com.github.bjoernpetersen.spotify

import com.github.bjoernpetersen.musicbot.spi.config.ConfigChecker

internal fun <T> nullConfigChecker(): ConfigChecker<T> = { if (it == null) "Required" else null }
