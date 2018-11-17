package com.github.bjoernpetersen.spotify.provider

import com.github.bjoernpetersen.musicbot.api.config.Config
import com.github.bjoernpetersen.musicbot.spi.plugin.Provider
import com.neovisionaries.i18n.CountryCode
import com.wrapper.spotify.SpotifyApi

interface SpotifyProviderBase : Provider {
    //val api: SpotifyApi
    val market: Config.SerializedEntry<CountryCode>
}
