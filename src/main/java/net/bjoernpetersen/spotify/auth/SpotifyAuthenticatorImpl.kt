package net.bjoernpetersen.spotify.auth

import io.ktor.http.encodeURLParameter
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import net.bjoernpetersen.musicbot.api.config.ActionButton
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer
import net.bjoernpetersen.musicbot.api.config.IntSerializer
import net.bjoernpetersen.musicbot.api.config.NonnullConfigChecker
import net.bjoernpetersen.musicbot.api.config.NumberBox
import net.bjoernpetersen.musicbot.api.config.PasswordBox
import net.bjoernpetersen.musicbot.api.config.SerializationException
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.musicbot.spi.util.BrowserOpener
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject

@KtorExperimentalAPI
class SpotifyAuthenticatorImpl : SpotifyAuthenticator {

    private val logger = KotlinLogging.logger { }
    private val lock: Lock = ReentrantLock()
    private val random = SecureRandom()

    override val name: String = "Spotify Auth"
    override val token: String
        get() = currentToken!!.value

    @Inject
    private lateinit var browserOpener: BrowserOpener

    private lateinit var port: Config.SerializedEntry<Int>
    private lateinit var clientId: Config.StringEntry

    private lateinit var tokenExpiration: Config.SerializedEntry<Instant>
    private lateinit var accessToken: Config.StringEntry

    private var currentToken: Token? = null
        get() {
            val current = field
            if (current == null) {
                field = initAuth()
            } else if (current.isExpired()) {
                field = authorize()
            }
            return field
        }
        set(value) {
            field = value
            tokenExpiration.set(value?.expiration)
            accessToken.set(value?.value)
        }

    @Throws(IOException::class, InterruptedException::class)
    private fun initAuth(): Token {
        if (accessToken.get() != null && tokenExpiration.get() != null) {
            val token = accessToken.get()!!
            val expirationDate = tokenExpiration.get()!!
            val result = Token(token, expirationDate)
            // if it's expired, this call will refresh the token
            if (!result.isExpired()) return result
        }
        return authorize()
    }

    private fun getSpotifyUrl(state: String, redirectUrl: URL): URL {
        try {
            return URL(SPOTIFY_URL.let { base ->
                listOf(
                    "client_id=$CLIENT_ID",
                    "redirect_uri=${redirectUrl.toExternalForm()}",
                    "response_type=token",
                    "scope=${SCOPES.joinToString(" ").encodeURLParameter()}",
                    "state=$state"
                ).joinToString("&", prefix = "$base?")
            })
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException(e)
        }
    }

    private fun generateRandomString(): String {
        return Integer.toString(random.nextInt(Integer.MAX_VALUE))
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun authorize(): Token {
        logger.debug("Acquiring auth lock...")
        if (!lock.tryLock(10, TimeUnit.SECONDS)) {
            logger.warn("Can't acquire Auth lock!")
            throw InterruptedException()
        }
        try {
            logger.debug("Lock acquired")

            return try {
                runBlocking {
                    val state = generateRandomString()
                    val callback = KtorCallback(port.get()!!)
                    val callbackJob = async { callback.start(state) }
                    val url = getSpotifyUrl(state, callback.callbackUrl)
                    browserOpener.openDocument(url)
                    callbackJob.await()
                }
            } catch (e: TimeoutTokenException) {
                logger.error { "No token received within one minute" }
                throw IOException(e)
            } catch (e: InvalidTokenException) {
                logger.error(e) { "Invalid token response received" }
                throw IOException(e)
            }
        } finally {
            lock.unlock()
        }
    }

    override fun initialize(initStateWriter: InitStateWriter) {
        initStateWriter.state("Retrieving token...")
        token.also { initStateWriter.state("Retrieved token.") }
    }

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> = listOf(
        config.SerializedEntry(
            "port",
            "OAuth callback port",
            IntSerializer,
            NonnullConfigChecker,
            NumberBox(1024, 65535),
            58642
        ).also { port = it })

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> {
        tokenExpiration = secrets.SerializedEntry(
            "tokenExpiration",
            "Token expiration date",
            InstantSerializer,
            NonnullConfigChecker,
            ActionButton("Refresh", ::toTimeString) {
                try {
                    val token = authorize()
                    this.currentToken = token
                    true
                } catch (e: Exception) {
                    false
                }
            })

        accessToken = secrets.StringEntry(
            "accessToken",
            "OAuth access token",
            { null },
            null
        )

        clientId = secrets.StringEntry(
            key = "clientId",
            description = "OAuth client ID. Only required if there is a custom port.",
            configChecker = { null },
            uiNode = PasswordBox,
            default = CLIENT_ID
        )

        return listOf(tokenExpiration, clientId)
    }

    override fun createStateEntries(state: Config) {}

    override fun close() {
    }

    private companion object {
        private const val SPOTIFY_URL = " https://accounts.spotify.com/authorize"
        private const val CLIENT_ID = "902fe6b9a4b6421caf88ee01e809939a"
        private val SCOPES = listOf(
            "user-modify-playback-state",
            "user-read-playback-state",
            "playlist-read-private",
            "playlist-read-collaborative"
        )

        private fun toTimeString(instant: Instant) = DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .format(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()))
    }
}

private object InstantSerializer : ConfigSerializer<Instant> {
    @Throws(SerializationException::class)
    override fun deserialize(string: String): Instant {
        return string.toLongOrNull()?.let(Instant::ofEpochSecond) ?: throw SerializationException()
    }

    override fun serialize(obj: Instant): String = obj.epochSecond.toString()
}
