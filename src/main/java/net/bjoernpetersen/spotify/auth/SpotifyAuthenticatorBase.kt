package net.bjoernpetersen.spotify.auth

import net.bjoernpetersen.musicbot.spi.plugin.Base
import net.bjoernpetersen.musicbot.spi.plugin.GenericPlugin

private const val DESCRIPTION = "Provides Spotify authentication"

@Base
interface SpotifyAuthenticatorBase : GenericPlugin {

    val token: String
    override val description: String
        get() = DESCRIPTION
}
