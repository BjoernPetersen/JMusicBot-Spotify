package net.bjoernpetersen.spotify.suggester

import net.bjoernpetersen.musicbot.api.Song
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.config.TextBox
import net.bjoernpetersen.musicbot.api.player.SongEntry
import net.bjoernpetersen.musicbot.api.plugin.IdBase
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import net.bjoernpetersen.musicbot.spi.plugin.Suggester
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.spotify.provider.SpotifyProviderBase
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject

@IdBase
class SongRepeatSuggester : Suggester {

    @Inject
    private lateinit var provider: SpotifyProviderBase
    private lateinit var songUrl: Config.StringEntry
    private lateinit var song: Song

    override val name: String = "Spotify repeater"
    override val description: String = "TODO"

    override val subject: String
        get() = song.title

    override fun suggestNext(): Song {
        return song
    }

    override fun getNextSuggestions(maxLength: Int): List<Song> = listOf(song)

    override fun removeSuggestion(song: Song) {}

    override fun notifyPlayed(songEntry: SongEntry) {}

    override fun dislike(song: Song) {}

    private fun getSongId(url: String): String? {
        return try {
            val parsed = URL(url)
            val pathParts = parsed.path.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            if (pathParts.size != 3) {
                null
            } else pathParts[2]
        } catch (e: MalformedURLException) {
            null
        }
    }

    @Throws(InitializationException::class)
    override fun initialize(initStateWriter: InitStateWriter) {
        try {
            song = songUrl.get()?.let { getSongId(it) }?.let(provider::lookup)
                ?: throw InitializationException("Could not find song")
        } catch (e: NoSuchSongException) {
            throw InitializationException(e)
        }
    }

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> {
        songUrl = config.StringEntry(
            "songUrl",
            "A Spotify song link",
            { if (it?.let(::getSongId) == null) "Invalid URL" else null },
            TextBox, null)
        return listOf(songUrl)
    }

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> = emptyList()

    override fun createStateEntries(state: Config) {}

    override fun close() {}
}
