package net.bjoernpetersen.spotify.suggester

import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import com.wrapper.spotify.model_objects.specification.Paging
import com.wrapper.spotify.model_objects.specification.PlaylistTrack
import mu.KotlinLogging
import net.bjoernpetersen.musicbot.api.config.ChoiceBox
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.player.Song
import net.bjoernpetersen.musicbot.api.plugin.IdBase
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.spotify.auth.SpotifyAuthenticatorBase
import net.bjoernpetersen.spotify.nullConfigChecker
import net.bjoernpetersen.spotify.provider.SpotifyProviderBase
import java.io.IOException
import java.util.Collections
import java.util.LinkedList
import javax.inject.Inject

@IdBase
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
    override val description: String = "TODO"
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
        playlistId = config.SerializedEntry(
            "playlist",
            "One of your public playlists to play",
            PlaylistChoice.Serializer,
            nullConfigChecker(),
            ChoiceBox(PlaylistChoice::displayName, { findPlaylists() }, true))
        return listOf(shuffle, playlistId)
    }

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> {
        userId = secrets.StringEntry(
            "userId",
            "",
            nullConfigChecker(),
            null)
        return emptyList()
    }

    override fun createStateEntries(state: Config) {
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
