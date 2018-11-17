package com.github.bjoernpetersen.spotify.auth

import com.github.bjoernpetersen.musicbot.api.config.ConfigSerializer
import com.github.bjoernpetersen.musicbot.api.config.SerializationException
import java.time.Instant

internal object InstantSerializer : ConfigSerializer<Instant> {
    @Throws(SerializationException::class)
    override fun deserialize(string: String): Instant {
        return string.toLongOrNull()?.let(Instant::ofEpochSecond) ?: throw SerializationException()
    }

    override fun serialize(obj: Instant): String = obj.epochSecond.toString()
}
