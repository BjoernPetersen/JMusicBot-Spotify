package net.bjoernpetersen.spotify.playback

import com.google.gson.JsonArray
import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.detailed.BadGatewayException
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import mu.KotlinLogging
import net.bjoernpetersen.musicbot.spi.plugin.AbstractPlayback
import net.bjoernpetersen.musicbot.spi.plugin.PlaybackState
import net.bjoernpetersen.musicbot.spi.plugin.PlaybackStateListener
import net.bjoernpetersen.spotify.auth.SpotifyAuthenticator
import java.time.Duration

internal class SpotifyPlayback(
    private val authenticator: SpotifyAuthenticator,
    private val deviceId: String,
    private val songId: String
) : AbstractPlayback() {

    private val logger = KotlinLogging.logger {}
    private suspend fun getApi() = SpotifyApi.builder()
        .setAccessToken(authenticator.getToken())
        .build()

    private val songUri = "spotify:track:$songId"
    private var isStarted = false

    private lateinit var listener: PlaybackStateListener

    override fun setPlaybackStateListener(listener: PlaybackStateListener) {
        this.listener = listener
    }

    override suspend fun pause() {
        withContext(coroutineContext) {
            try {
                getApi().pauseUsersPlayback()
                    .device_id(deviceId)
                    .build()
                    .execute()
            } catch (e: Exception) {
                logger.error(e) { "Could not pause playback" }
                listener(PlaybackState.BROKEN)
            }
        }
    }

    private fun launchStateChecker() {
        launch {
            while (!isDone()) {
                delay(Duration.ofSeconds(1))
                checkState()
            }
        }
    }

    override suspend fun play() {
        withContext(coroutineContext) {
            try {
                getApi().startResumeUsersPlayback()
                    .device_id(deviceId)
                    .apply {
                        if (!isStarted) {
                            logger.debug { "Starting song" }
                            uris(JsonArray().apply {
                                add(songUri)
                            })
                            launchStateChecker()
                        }
                    }
                    .build()
                    .execute()
                isStarted = true
            } catch (e: Exception) {
                logger.error(e) { "Could not play playback" }
                listener(PlaybackState.BROKEN)
            }
        }
    }

    private suspend fun checkState() {
        val state = try {
            getApi().informationAboutUsersCurrentPlayback
                .build()
                .execute()
        } catch (e: Exception) {
            logger.error(e) { "Could not check state" }
            if (e !is BadGatewayException) {
                listener(PlaybackState.BROKEN)
            }
            return
        }

        yield()

        state.apply {
            val playbackState = if (!is_playing) {
                if (
                    progress_ms == null || progress_ms == 0
                    || item == null || item.id != songId
                ) return markDone()

                PlaybackState.PAUSE
            } else if (item != null && item.id != songId) {
                return markDone()
            } else {
                PlaybackState.PLAY
            }

            if (!isDone()) listener(playbackState)
        }
    }

    override suspend fun close() {
        // Mark done so the state checker doesn't react to pausing
        markDone()
        // Pause, so the playback actually stops
        pause()
        super.close()
    }
}
