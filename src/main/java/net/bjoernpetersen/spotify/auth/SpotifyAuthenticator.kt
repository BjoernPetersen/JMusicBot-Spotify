package net.bjoernpetersen.spotify.auth

import net.bjoernpetersen.musicbot.api.plugin.Base
import net.bjoernpetersen.musicbot.spi.plugin.GenericPlugin

private const val DESCRIPTION = "Provides Spotify authentication"

@Base
interface SpotifyAuthenticator : GenericPlugin {

    val token: String
    override val description: String
        get() = DESCRIPTION
}
