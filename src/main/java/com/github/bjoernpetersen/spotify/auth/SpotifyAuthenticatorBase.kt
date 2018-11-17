package com.github.bjoernpetersen.spotify.auth

import com.github.bjoernpetersen.musicbot.spi.plugin.GenericPlugin

interface SpotifyAuthenticatorBase : GenericPlugin {
    val token: String
}
