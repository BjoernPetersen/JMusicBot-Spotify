package net.bjoernpetersen.spotify.playback

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.JsonArray
import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import mu.KotlinLogging
import net.bjoernpetersen.musicbot.spi.plugin.AbstractPlayback
import net.bjoernpetersen.musicbot.spi.plugin.PlaybackState
import net.bjoernpetersen.musicbot.spi.plugin.PlaybackStateListener
import net.bjoernpetersen.spotify.auth.SpotifyAuthenticator
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class SpotifyPlayback(
    private val authenticator: SpotifyAuthenticator,
    private val deviceId: String,
    private val songId: String) : AbstractPlayback() {

    private val logger = KotlinLogging.logger {}
    private val api = SpotifyApi.Builder()
        .setAccessToken(authenticator.token)
        .build()
        get() = field.apply {
            accessToken = authenticator.token
        }

    private val songUri = "spotify:track:$songId"
    private var isStarted = false

    private val stateChecker = Executors.newSingleThreadScheduledExecutor(
        ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("Spotify-state-checker-%d")
            .build())

    private lateinit var listener: PlaybackStateListener

    override fun setPlaybackStateListener(listener: PlaybackStateListener) {
        this.listener = listener
    }

    override fun pause() {
        try {
            api.pauseUsersPlayback()
                .device_id(deviceId)
                .build()
                .execute()
        } catch (e: SpotifyWebApiException) {
            logger.error(e) { "Could not pause playback" }
            listener(PlaybackState.BROKEN)
        }
    }

    override fun play() {
        try {
            api.startResumeUsersPlayback()
                .device_id(deviceId)
                .apply {
                    if (!isStarted) {
                        logger.debug { "Starting song" }
                        uris(JsonArray().apply {
                            add(songUri)
                        })
                        stateChecker.scheduleWithFixedDelay(::checkState,
                            2000, 3000, TimeUnit.MILLISECONDS)
                    }
                }
                .build()
                .execute()
            isStarted = true
        } catch (e: SpotifyWebApiException) {
            logger.error(e) { "Could not play playback" }
            listener(PlaybackState.BROKEN)
        }
    }

    private fun checkState() {
        val state = try {
            api.informationAboutUsersCurrentPlayback
                .build()
                .execute()!!
        } catch (e: SpotifyWebApiException) {
            logger.error(e) { "Could not check state" }
            listener(PlaybackState.BROKEN)
            return
        }

        state.apply {
            listener(if (!is_playing) {
                if (progress_ms == null || progress_ms == 0
                    || item == null || item.id != songId) {
                    return markDone().also { stateChecker.shutdown() }
                }
                PlaybackState.PAUSE
            } else if (item != null && item.id != songId) {
                return markDone().also { stateChecker.shutdown() }
            } else {
                PlaybackState.PLAY
            })
        }
    }

    override fun close() {
        if (!stateChecker.awaitTermination(2, TimeUnit.SECONDS)) {
            stateChecker.shutdownNow();
        }
        pause()
        super.close()
    }
}