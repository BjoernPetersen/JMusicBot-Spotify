package net.bjoernpetersen.spotify.control

import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import com.wrapper.spotify.model_objects.miscellaneous.Device
import mu.KotlinLogging
import net.bjoernpetersen.musicbot.api.config.ChoiceBox
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer
import net.bjoernpetersen.musicbot.api.config.NonnullConfigChecker
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.spotify.auth.SpotifyAuthenticator
import javax.inject.Inject

class SpotifyControlImpl : SpotifyControl {

    private val logger = KotlinLogging.logger { }

    @Inject
    private lateinit var authenticator: SpotifyAuthenticator
    private lateinit var device: Config.SerializedEntry<SimpleDevice>

    override val deviceId: String
        get() = device.get()?.id ?: throw IllegalStateException()

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

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> {
        device = config.SerializedEntry(
            "device",
            "Spotify device to use",
            DeviceSerializer,
            NonnullConfigChecker,
            ChoiceBox(SimpleDevice::name, { findDevices() }, true)
        )
        return listOf(device)
    }

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> = emptyList()
    override fun createStateEntries(state: Config) {}

    override fun initialize(initStateWriter: InitStateWriter) {
        initStateWriter.state("Checking device config")
        if (device.get() == null) {
            throw InitializationException("No device selected")
        }
    }

    override fun close() {}
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
        return "${obj.id};${obj.name}"
    }
}
