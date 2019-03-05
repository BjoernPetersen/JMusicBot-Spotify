package net.bjoernpetersen.spotify.suggester

import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import mu.KotlinLogging
import net.bjoernpetersen.musicbot.api.config.ChoiceBox
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.config.NonnullConfigChecker
import net.bjoernpetersen.musicbot.api.player.Song
import net.bjoernpetersen.musicbot.api.plugin.IdBase
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.spotify.auth.SpotifyAuthenticator
import net.bjoernpetersen.spotify.marketFromToken
import net.bjoernpetersen.spotify.provider.SpotifyProvider
import java.io.IOException
import java.util.Collections
import java.util.LinkedList
import javax.inject.Inject

@IdBase("Spotify playlist")
class PlaylistSuggester : Suggester {

    private val logger = KotlinLogging.logger {}

    private lateinit var userId: Config.StringEntry
    private lateinit var playlistId: Config.SerializedEntry<PlaylistChoice>
    private lateinit var shuffle: Config.BooleanEntry

    @Inject
    private lateinit var auth: SpotifyAuthenticator
    @Inject
    private lateinit var provider: SpotifyProvider

    private var api: SpotifyApi? = null
    private var playlist: PlaylistChoice? = null
    private lateinit var playlistSongs: List<Song>

    private var nextIndex: Int = 0
    private var nextSongs: LinkedList<Song> = LinkedList()

    override val name: String = "Spotify playlist"
    override val description: String = "Plays songs from one of your public Spotify playlists"
    override val subject: String
        get() = playlist?.displayName ?: name

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
            nextSongs.add(playlistSongs[nextIndex])
            nextIndex = (nextIndex + 1) % playlistSongs.size
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
            true
        )
        playlistId = config.SerializedEntry(
            "playlist",
            "One of your public playlists to play",
            PlaylistChoice.Serializer,
            NonnullConfigChecker,
            ChoiceBox(PlaylistChoice::displayName, { findPlaylists() }, true)
        )
        return listOf(shuffle, playlistId)
    }

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> {
        userId = secrets.StringEntry(
            "userId",
            "",
            NonnullConfigChecker,
            null
        )
        return emptyList()
    }

    override fun createStateEntries(state: Config) {}

    override fun initialize(initStateWriter: InitStateWriter) {
        initStateWriter.state("Loading user ID")
        if (userId.get() == null) {
            userId.set(loadUserId())
        }

        initStateWriter.state("Loading playlist songs")
        playlist = playlistId.get()
        playlistSongs = playlist?.id?.let { playlistId ->
            loadPlaylist(playlistId)
                .let { if (shuffle.get()) it.shuffled() else it }
                .also { logger.info { "Loaded ${it.size} songs" } }
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
    private fun loadPlaylist(playlistId: String, offset: Int = 0): List<Song> {
        val playlistTracks = try {
            getApi()
                .getPlaylistsTracks(playlistId)
                .marketFromToken()
                .offset(offset)
                .build().execute()
        } catch (e: IOException) {
            throw InitializationException("Could not load playlist", e)
        } catch (e: SpotifyWebApiException) {
            throw InitializationException("Could not load playlist", e)
        }

        val ids = playlistTracks.items
            .asSequence()
            .map { it.track }
            .filter { it.isPlayable }
            .map { it.id }
            .toList()

        val result = provider.lookupBatch(ids)

        return if (playlistTracks.next == null) result
        else result + loadPlaylist(playlistId, offset + playlistTracks.items.size)
    }

    @Throws(IOException::class)
    override fun close() {
        nextSongs.clear()
        api = null
    }

}
