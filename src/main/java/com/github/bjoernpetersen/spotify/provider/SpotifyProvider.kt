package com.github.bjoernpetersen.spotify.provider

import com.github.bjoernpetersen.musicbot.api.NamedPlugin
import com.github.bjoernpetersen.musicbot.api.Song
import com.github.bjoernpetersen.musicbot.api.config.ChoiceBox
import com.github.bjoernpetersen.musicbot.api.config.Config
import com.github.bjoernpetersen.musicbot.spi.plugin.Bases
import com.github.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import com.github.bjoernpetersen.musicbot.spi.plugin.Playback
import com.github.bjoernpetersen.musicbot.spi.plugin.PlaybackSupplier
import com.github.bjoernpetersen.musicbot.spi.plugin.Provider
import com.github.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import com.github.bjoernpetersen.spotify.CountryCodeSerializer
import com.github.bjoernpetersen.spotify.auth.SpotifyAuthenticatorBase
import com.github.bjoernpetersen.spotify.playback.SpotifyPlaybackFactory
import com.google.common.collect.Lists
import com.neovisionaries.i18n.CountryCode
import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import com.wrapper.spotify.model_objects.specification.Track
import mu.KotlinLogging
import java.io.IOException
import java.util.*
import javax.inject.Inject

@Bases(SpotifyProviderBase::class)
class SpotifyProvider : SpotifyProviderBase {

    private val logger = KotlinLogging.logger {}
    @Inject
    private lateinit var authenticator: SpotifyAuthenticatorBase
    @Inject
    private lateinit var spotifyPlaybackFactory: SpotifyPlaybackFactory
    override lateinit var market: Config.SerializedEntry<CountryCode>
        private set

    private var api: SpotifyApi? = null
        get() {
            field!!.accessToken = authenticator.token
            return field
        }
        set(value) {
            field = value!!
        }

    override val name: String = "Spotify"
    override val subject: String = "Spotify"
    private val provider = NamedPlugin<Provider>(
        SpotifyProviderBase::class, subject)

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> {
        market = config.SerializedEntry(
            "market",
            "Country code of your Spotify account",
            CountryCodeSerializer,
            { if (it == CountryCode.UNDEFINED) "Required" else null },
            ChoiceBox({ CountryCode.values().toList() }),
            CountryCode.DE
        )
        return listOf<Config.Entry<*>>(market)
    }

    override fun createSecretEntries(config: Config): List<Config.Entry<*>> = emptyList()
    override fun createStateEntries(config: Config) = Unit

    override fun initialize(initStateWriter: InitStateWriter) {
        api = SpotifyApi.builder()
            .setAccessToken(authenticator.token)
            .build()
    }

    override fun getPlaybackSupplier(song: Song): PlaybackSupplier = object : PlaybackSupplier {
        override fun supply(song: Song): Playback {
            return spotifyPlaybackFactory.getPlayback(song.id)
        }
    }

    override fun loadSong(song: Song): Boolean = true

    override fun close() {
    }

    private fun createSong(id: String, title: String, description: String, durationMs: Int,
        albumArtUrl: String?): Song {
        return Song(
            id = id,
            provider = provider,
            title = title,
            description = description,
            duration = durationMs / 1000,
            albumArtUrl = albumArtUrl)
    }

    override fun search(query: String): List<Song> {
        if (query.isEmpty()) {
            return emptyList()
        }
        return try {
            api!!.searchTracks(query)
                .limit(40)
                .market(market.get())
                .build().execute().items
                .map { this.songFromTrack(it) }
        } catch (e: IOException) {
            logger.error(e) { "Error searching for spotify songs (query: $query)" }
            emptyList()
        } catch (e: SpotifyWebApiException) {
            emptyList()
        }
    }

    private fun songFromTrack(track: Track): Song {
        val id = track.id
        val title = track.name
        val description = track.artists.asSequence()
            .map { it.name }
            .joinToString()
        val durationMs = track.durationMs
        val images = track.album.images
        val albumArtUrl = if (images.isEmpty()) null else images[0].url
        return createSong(id, title, description, durationMs, albumArtUrl)
    }

    @Throws(NoSuchSongException::class)
    override fun lookup(id: String): Song {
        try {
            return songFromTrack(api!!.getTrack(id).build().execute())
        } catch (e: IOException) {
            throw NoSuchSongException("Error looking up song: $id", SpotifyProvider::class, e)
        } catch (e: SpotifyWebApiException) {
            throw NoSuchSongException("Error looking up song: $id", SpotifyProvider::class, e)
        }
    }

    override fun lookupBatch(ids: List<String>): List<Song> {
        val result = ArrayList<Song>(ids.size)
        for (subIds in Lists.partition(ids, 50)) {
            try {
                api!!.getSeveralTracks(*subIds.toTypedArray()).build().execute()
                    .map { this.songFromTrack(it) }
                    .forEach { result.add(it) }
            } catch (e: IOException) {
                logger.info(e) { "Could not look up some ID." }
            } catch (e: SpotifyWebApiException) {
                logger.info(e) { "Could not look up some ID." }
            }
        }
        return Collections.unmodifiableList(result)
    }
}
