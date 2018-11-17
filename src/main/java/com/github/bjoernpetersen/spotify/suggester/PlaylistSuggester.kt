package com.github.bjoernpetersen.spotify.suggester

import com.github.bjoernpetersen.musicbot.api.Song
import com.github.bjoernpetersen.musicbot.api.config.ChoiceBox
import com.github.bjoernpetersen.musicbot.api.config.Config
import com.github.bjoernpetersen.musicbot.spi.plugin.Bases
import com.github.bjoernpetersen.musicbot.spi.plugin.InitializationException
import com.github.bjoernpetersen.musicbot.spi.plugin.Suggester
import com.github.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import com.github.bjoernpetersen.spotify.auth.SpotifyAuthenticatorBase
import com.github.bjoernpetersen.spotify.nullConfigChecker
import com.github.bjoernpetersen.spotify.provider.SpotifyProviderBase
import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import com.wrapper.spotify.model_objects.specification.Paging
import com.wrapper.spotify.model_objects.specification.PlaylistTrack
import mu.KotlinLogging
import java.io.IOException
import java.util.*
import javax.inject.Inject

@Bases(Suggester::class, PlaylistSuggester::class)
class PlaylistSuggester : Suggester {

    private val logger = KotlinLogging.logger {}

    private lateinit var userId: Config.StringEntry
    private lateinit var playlistId: Config.SerializedEntry<PlaylistChoice>
    private lateinit var shuffle: Config.BooleanEntry

    @Inject
    private lateinit var auth: SpotifyAuthenticatorBase
    @Inject
    private lateinit var provider: SpotifyProviderBase

    private var api: SpotifyApi? = null
    private lateinit var playlist: List<Song>

    private var nextIndex: Int = 0
    private var nextSongs: LinkedList<Song> = LinkedList()

    override val name: String = "Spotify playlist"
    override var subject: String = name
        private set

    private fun findPlaylists(): List<PlaylistChoice>? {
        var userId = userId.get()
        if (userId == null) {
            logger.debug("No userId set, trying to retrieve it...")
            userId = try {
                loadUserId()
            } catch (e: InitializationException) {
                logger.info("user ID could not be found.")
                return null
            }
            this.userId.set(userId)
        }
        val playlists = try {
            getApi()
                .getListOfUsersPlaylists(userId)
                .limit(50)
                .build().execute()!!
        } catch (e: Throwable) {
            logger.error(e) { "Could not retrieve playlists" }
            return null
        }

        return playlists.items.map {
            PlaylistChoice(it.id, it.name)
        }
    }

    override fun suggestNext(): Song {
        val song = getNextSuggestions(1)[0]
        removeSuggestion(song)
        return song
    }

    override fun getNextSuggestions(maxLength: Int): List<Song> {
        val startIndex = nextIndex
        while (nextSongs.size < Math.max(Math.min(50, maxLength), 1)) {
            // load more suggestions
            nextSongs.add(playlist[nextIndex])
            nextIndex = (nextIndex + 1) % playlist.size
            if (nextIndex == startIndex) {
                // the playlist is shorter than maxLength
                break
            }
        }
        return Collections.unmodifiableList(nextSongs)
    }

    override fun removeSuggestion(song: Song) {
        nextSongs.remove(song)
    }

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> {
        shuffle = config.BooleanEntry(
            "shuffle",
            "Whether the playlist should be shuffled",
            true)
        return listOf(shuffle)
    }

    override fun createSecretEntries(config: Config): List<Config.Entry<*>> {
        userId = config.StringEntry(
            "userId",
            "",
            nullConfigChecker(),
            null)
        return emptyList()
    }

    override fun createStateEntries(config: Config) {
        playlistId = config.SerializedEntry(
            "playlistId",
            "",
            PlaylistChoice.Serializer,
            nullConfigChecker(),
            ChoiceBox({ findPlaylists() }, true))
    }

    fun initializeConfigEntries(config: Config): List<Config.Entry<*>> {
        return emptyList()
    }

    override fun initialize(initStateWriter: InitStateWriter) {
        if (userId.get() == null) {
            userId.set(loadUserId())
        }

        playlist = playlistId.get()?.id?.let {
            loadPlaylist(it)
        } ?: throw InitializationException("No playlist selected")
        nextSongs = LinkedList()
    }


    private fun getApi(): SpotifyApi {
        api?.apply { return this }

        val token = auth.token

        return SpotifyApi.builder().setAccessToken(token).build().also {
            api = it
        }
    }

    @Throws(InitializationException::class)
    private fun loadUserId(): String {
        try {
            return getApi().currentUsersProfile.build().execute().id
        } catch (e: IOException) {
            throw InitializationException("Could not get user ID", e)
        } catch (e: SpotifyWebApiException) {
            throw InitializationException("Could not get user ID", e)
        }
    }

    @Throws(InitializationException::class)
    private fun loadPlaylist(playlistId: String): List<Song> {
        val playlist: Paging<PlaylistTrack>
        try {
            playlist = getApi()
                .getPlaylistsTracks(playlistId)
                .build().execute()
        } catch (e: IOException) {
            throw InitializationException("Could not load playlist", e)
        } catch (e: SpotifyWebApiException) {
            throw InitializationException("Could not load playlist", e)
        }

        val tracks = playlist.items
        val ids = tracks
            .map { t -> t.track.id }
            .let { if (shuffle.get()) it.shuffled() else it }

        // TODO add the full list instead of just the first page (100 tracks). API wrapper limitation.
        return provider.lookupBatch(ids)
    }

    @Throws(IOException::class)
    override fun close() {
        nextSongs.clear()
        api = null
    }

}
