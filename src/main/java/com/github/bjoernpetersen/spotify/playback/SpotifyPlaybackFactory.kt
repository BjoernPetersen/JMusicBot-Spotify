package com.github.bjoernpetersen.spotify.playback

import com.github.bjoernpetersen.musicbot.api.config.ChoiceBox
import com.github.bjoernpetersen.musicbot.api.config.Config
import com.github.bjoernpetersen.musicbot.api.config.ConfigSerializer
import com.github.bjoernpetersen.musicbot.spi.plugin.Bases
import com.github.bjoernpetersen.musicbot.spi.plugin.InitializationException
import com.github.bjoernpetersen.musicbot.spi.plugin.Playback
import com.github.bjoernpetersen.musicbot.spi.plugin.PlaybackFactory
import com.github.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import com.github.bjoernpetersen.spotify.auth.SpotifyAuthenticatorBase
import com.github.bjoernpetersen.spotify.nullConfigChecker
import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import com.wrapper.spotify.model_objects.miscellaneous.Device
import mu.KotlinLogging
import javax.inject.Inject

@Bases(PlaybackFactory::class, SpotifyPlaybackFactory::class)
class SpotifyPlaybackFactory : PlaybackFactory {

    private val logger = KotlinLogging.logger { }

    @Inject
    private lateinit var authenticator: SpotifyAuthenticatorBase
    private lateinit var device: Config.SerializedEntry<SimpleDevice>

    private fun findDevices(): List<SimpleDevice>? {
        return try {
            SpotifyApi.builder()
                .setAccessToken(authenticator.token)
                .build()
                .usersAvailableDevices
                .build()
                .execute()
                .map(::SimpleDevice)
        } catch (e: SpotifyWebApiException) {
            logger.error(e) { "Could not retrieve device list" }
            null
        }
    }

    override val name: String = "Spotify"

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> {
        device = config.SerializedEntry(
            "deviceId",
            "Spotify device to use",
            DeviceSerializer,
            nullConfigChecker(),
            ChoiceBox(SimpleDevice::name, { findDevices() }, true)
        )
        return listOf(device)
    }

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> = emptyList()
    override fun createStateEntries(state: Config) {}

    @Throws(InitializationException::class)
    override fun initialize(initStateWriter: InitStateWriter) {
        if (device.get() == null) {
            throw InitializationException("No device selected")
        }
        authenticator.token.also {
            throw InitializationException("Not authenticated")
        }
    }

    fun getPlayback(songId: String): Playback {
        return SpotifyPlayback(authenticator, device.get()!!.id, songId)
    }

    override fun close() {
    }
}

private data class SimpleDevice(val id: String, val name: String) {
    constructor(device: Device) : this(device.id, device.name)
}

private object DeviceSerializer : ConfigSerializer<SimpleDevice> {
    override fun deserialize(string: String): SimpleDevice {
        return string.split(';').let {
            val id = it[0]
            val name = it.subList(1, it.size).joinToString(";")
            SimpleDevice(id, name)
        }
    }

    override fun serialize(obj: SimpleDevice): String {
        return obj.id
    }

}
