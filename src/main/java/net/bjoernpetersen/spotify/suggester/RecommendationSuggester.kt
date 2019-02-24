package net.bjoernpetersen.spotify.suggester

import com.wrapper.spotify.SpotifyApi
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer
import net.bjoernpetersen.musicbot.api.config.SerializationException
import net.bjoernpetersen.musicbot.api.config.TextBox
import net.bjoernpetersen.musicbot.api.player.Song
import net.bjoernpetersen.musicbot.api.player.SongEntry
import net.bjoernpetersen.musicbot.api.plugin.IdBase
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.spotify.auth.SpotifyAuthenticator
import net.bjoernpetersen.spotify.provider.SpotifyProvider
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.min

@IdBase("Spotify recommendation suggester")
class RecommendationSuggester : Suggester {

    override val name: String = "Spotify recommendation suggester"
    override val description: String =
        "Suggests Spotify songs based on the last played manually enqueued song"

    @Inject
    private lateinit var auth: SpotifyAuthenticator
    @Inject
    private lateinit var provider: SpotifyProvider

    private lateinit var fallbackEntry: Config.StringEntry
    private lateinit var baseEntry: Config.SerializedEntry<SimpleSong>

    private val baseId: String
        get() = baseEntry.get()?.id
            ?: getSongId(fallbackEntry.get()!!)
            ?: throw IllegalStateException("No valid base or fallback set")

    private val baseSong: Song
        get() {
            val baseId = baseId
            return provider.lookup(baseId)
        }

    override val subject: String
        get() = "Based on ${baseEntry.get()?.name ?: baseSong.title}"

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> {
        fallbackEntry = config.StringEntry(
            key = "fallbackEntry",
            description = "Spotify song URL of a song to base stations on if there is no alternative",
            configChecker = { if (it?.let(::getSongId) == null) "Invalid URL" else null },
            uiNode = TextBox,
            default = "https://open.spotify.com/track/75n8FqbBeBLW2jUzvjdjXV?si=3LjPfzQdTcmnMn05gf7UNQ")

        return listOf(fallbackEntry)
    }

    override fun createStateEntries(state: Config) {
        baseEntry = state.SerializedEntry(
            key = "baseEntry",
            description = "",
            serializer = SimpleSong.Serializer,
            configChecker = { null })
    }

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> = emptyList()

    override fun initialize(initStateWriter: InitStateWriter) {
        initStateWriter.state("Trying to retrieve base song")
        val baseSong = try {
            baseSong
        } catch (e: NoSuchSongException) {
            throw InitializationException(e)
        } catch (e: IllegalStateException) {
            throw InitializationException(e)
        }

        initStateWriter.state("Filling suggestions")
        fillNextSongs(baseSong)
    }

    private val nextSongs: MutableList<Song> = LinkedList()

    private fun fillNextSongs(base: Song) {
        val api = SpotifyApi.builder()
            .setAccessToken(auth.token)
            .build()

        // TODO base on multiple songs
        api.recommendations
            .market(provider.market.get())
            .seed_tracks(base.id)
            .build()
            .execute()
            .tracks
            .map { provider.trackToSong(it) }
            .forEach { nextSongs.add(it) }
    }

    override fun getNextSuggestions(maxLength: Int): List<Song> {
        if (nextSongs.size <= 1) {
            fillNextSongs(baseSong)
        }

        return nextSongs.toList().let { it.subList(0, min(maxLength, it.size)) }
    }

    override fun suggestNext(): Song {
        val song = getNextSuggestions(1).first()
        nextSongs.remove(song)
        return song
    }

    override fun removeSuggestion(song: Song) {
        nextSongs.remove(song)
    }

    override fun notifyPlayed(songEntry: SongEntry) {
        super.notifyPlayed(songEntry)
        if (songEntry.user != null) {
            val song = songEntry.song
            baseEntry.set(SimpleSong(song.id, song.title))
            nextSongs.clear()
        }
    }

    override fun close() {}
}

private data class SimpleSong(val id: String, val name: String) {
    object Serializer : ConfigSerializer<SimpleSong> {
        override fun serialize(obj: SimpleSong): String {
            return "${obj.id};${obj.name}"
        }

        override fun deserialize(string: String): SimpleSong {
            val splits = string.split(";")
            if (splits.size < 2) throw SerializationException()
            val id = splits[0]
            val name = splits.subList(1, splits.size).joinToString(";")
            return SimpleSong(id, name)
        }
    }
}
