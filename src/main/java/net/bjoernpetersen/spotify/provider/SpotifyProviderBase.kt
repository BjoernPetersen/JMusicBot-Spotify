package net.bjoernpetersen.spotify.provider

import com.neovisionaries.i18n.CountryCode
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.spi.plugin.IdBase
import net.bjoernpetersen.musicbot.spi.plugin.Provider

@IdBase
interface SpotifyProviderBase : Provider {

    //val api: SpotifyApi
    val market: Config.SerializedEntry<CountryCode>
}
