package com.github.bjoernpetersen.spotify

import com.github.bjoernpetersen.musicbot.api.config.ConfigSerializer
import com.github.bjoernpetersen.musicbot.api.config.SerializationException
import com.neovisionaries.i18n.CountryCode

internal object CountryCodeSerializer : ConfigSerializer<CountryCode> {
    override fun deserialize(string: String): CountryCode {
        return CountryCode.getByAlpha2Code(string) ?: throw SerializationException()
    }

    override fun serialize(obj: CountryCode): String {
        return obj.alpha2
    }

}
