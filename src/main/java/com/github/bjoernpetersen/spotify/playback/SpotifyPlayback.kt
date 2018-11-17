package com.github.bjoernpetersen.spotify.playback

import com.github.bjoernpetersen.musicbot.spi.plugin.AbstractPlayback
import com.github.bjoernpetersen.musicbot.spi.plugin.PlaybackState
import com.github.bjoernpetersen.musicbot.spi.plugin.PlaybackStateListener
import com.github.bjoernpetersen.spotify.auth.SpotifyAuthenticatorBase
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.JsonArray
import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import mu.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class SpotifyPlayback(
    private val authenticator: SpotifyAuthenticatorBase,
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
        }
    }

    override fun play() {
        try {
            api.startResumeUsersPlayback()
                .device_id(deviceId)
                .also {
                    if (!isStarted) {
                        it.uris(JsonArray().apply {
                            songUri
                        })
                        stateChecker.scheduleWithFixedDelay(::checkState,
                            2000, 3000, TimeUnit.MILLISECONDS)
                    }
                }
                .build()
                .execute()
            isStarted = true
        } catch (e: SpotifyWebApiException) {
            logger.error(e) { "Could not pause playback" }
        }
    }

    private fun checkState() {
        val state = try {
            api.informationAboutUsersCurrentPlayback
                .build()
                .execute()!!
        } catch (e: SpotifyWebApiException) {
            logger.error(e) { "Could not check state" }
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
        pause()
        if (!stateChecker.awaitTermination(2, TimeUnit.SECONDS)) {
            stateChecker.shutdownNow();
        }
        super.close()
    }
}
