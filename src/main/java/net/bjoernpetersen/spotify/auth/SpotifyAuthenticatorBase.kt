package net.bjoernpetersen.spotify.auth

import net.bjoernpetersen.musicbot.spi.plugin.GenericPlugin

private const val DESCRIPTION = "Provides Spotify authentication"

interface SpotifyAuthenticatorBase : GenericPlugin {
    val token: String
    override val description: String
        get() = DESCRIPTION
}
