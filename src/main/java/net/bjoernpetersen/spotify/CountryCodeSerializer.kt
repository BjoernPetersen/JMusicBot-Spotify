package net.bjoernpetersen.spotify

import com.neovisionaries.i18n.CountryCode
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer
import net.bjoernpetersen.musicbot.api.config.SerializationException

internal object CountryCodeSerializer : ConfigSerializer<CountryCode> {
    override fun deserialize(string: String): CountryCode {
        return CountryCode.getByAlpha2Code(string) ?: throw SerializationException()
    }

    override fun serialize(obj: CountryCode): String {
        return obj.alpha2
    }
}
