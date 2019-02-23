package net.bjoernpetersen.spotify.playback

import mu.KotlinLogging
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.plugin.Base
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException
import net.bjoernpetersen.musicbot.spi.plugin.Playback
import net.bjoernpetersen.musicbot.spi.plugin.PlaybackFactory
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.spotify.auth.SpotifyAuthenticator
import net.bjoernpetersen.spotify.control.SpotifyControl
import javax.inject.Inject

@Base
class SpotifyPlaybackFactory : PlaybackFactory {

    // TODO create base interface

    private val logger = KotlinLogging.logger { }

    @Inject
    private lateinit var authenticator: SpotifyAuthenticator
    @Inject
    private lateinit var control: SpotifyControl

    override val name: String = "Spotify"
    override val description: String = "Plays Spotify songs with an official Spotify client on " +
        "a possibly remote device. Requires a Spotify Premium subscription."

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> = emptyList()

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> = emptyList()
    override fun createStateEntries(state: Config) {}

    @Throws(InitializationException::class)
    override fun initialize(initStateWriter: InitStateWriter) {
        initStateWriter.state("Checking authentication")
        try {
            authenticator.token
        } catch (e: Exception) {
            throw InitializationException("Not authenticated", e)
        }
    }

    fun getPlayback(songId: String): Playback {
        return SpotifyPlayback(authenticator, control.deviceId, songId)
    }

    override fun close() {}
}
