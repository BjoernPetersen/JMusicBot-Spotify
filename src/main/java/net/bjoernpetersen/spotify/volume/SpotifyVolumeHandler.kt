package net.bjoernpetersen.spotify.volume

import com.wrapper.spotify.SpotifyApi
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.musicbot.spi.plugin.volume.VolumeHandler
import net.bjoernpetersen.spotify.auth.SpotifyAuthenticator
import net.bjoernpetersen.spotify.control.SpotifyControl
import javax.inject.Inject

class SpotifyVolumeHandler : VolumeHandler {
    override val name: String = "Spotify volume handler"
    override val description: String = "Controls the volume of a Spotify client"

    @Inject
    private lateinit var auth: SpotifyAuthenticator
    @Inject
    private lateinit var control: SpotifyControl

    fun getApi(): SpotifyApi = SpotifyApi.builder()
        .setAccessToken(auth.token)
        .build()

    override var volume: Int
        get() {
            val info = getApi()
                .informationAboutUsersCurrentPlayback
                .build()
                .execute() ?: return 100

            return info.device.volume_percent
        }
        set(value) {
            getApi().setVolumeForUsersPlayback(value).device_id(control.deviceId).build().execute()
        }


    override fun createConfigEntries(config: Config): List<Config.Entry<*>> = emptyList()

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> = emptyList()

    override fun createStateEntries(state: Config) {}

    override fun initialize(initStateWriter: InitStateWriter) {
        initStateWriter.state("Trying to access client...")
        val volume = try {
            volume
        } catch (e: Exception) {
            throw InitializationException(e)
        }
        initStateWriter.state("Volume is $volume")
    }

    override fun close() {}
}
