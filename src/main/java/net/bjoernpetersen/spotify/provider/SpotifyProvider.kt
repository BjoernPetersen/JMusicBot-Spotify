package net.bjoernpetersen.spotify.provider

import com.neovisionaries.i18n.CountryCode
import com.wrapper.spotify.model_objects.specification.Track
import com.wrapper.spotify.model_objects.specification.TrackSimplified
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.player.Song
import net.bjoernpetersen.musicbot.api.plugin.IdBase
import net.bjoernpetersen.musicbot.spi.plugin.Provider

@IdBase("Spotify")
interface SpotifyProvider : Provider {

    //val api: SpotifyApi
    val market: Config.SerializedEntry<CountryCode>

    fun trackToSong(track: Track): Song
    fun trackToSong(track: TrackSimplified): Song
}
