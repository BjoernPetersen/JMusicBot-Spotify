package com.github.bjoernpetersen.spotify.auth

import mu.KotlinLogging
import org.mortbay.jetty.Request
import org.mortbay.jetty.Server
import org.mortbay.jetty.handler.AbstractHandler
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

internal class LocalServerReceiver(port: Int, private val state: String) {

    private val logger = KotlinLogging.logger {}
    private val lock: Lock = ReentrantLock()
    private val done: Condition = lock.newCondition()

    private val server: Server = Server(port).also {
        for (c in it.connectors) {
            c.host = LOCALHOST
        }
        it.handler = Callback()
        try {
            it.start()
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    val redirectUrl = URL("http", LOCALHOST, port, CALLBACK_PATH)
    private val landingPage = loadHtml(LANDING_PAGE_FILE)
    private val redirectPage =
        loadHtml(REDIRECT_PAGE_FILE)
            .replaceFirst("%REDIRECT_URL%", redirectUrl.toExternalForm())

    private var received = false
    private var token: Token? = null

    private fun stop() {
        try {
            server.gracefulShutdown = 500
            server.stop()
            logger.debug("Jetty server stopped.")
        } catch (e: Exception) {
            logger.error(e) { "Could not close server" }
        }

    }

    @Throws(InterruptedException::class)
    private fun waitForToken(wait: () -> Unit): Token? {
        lock.lock()
        try {
            if (!received) {
                wait()
            }
            stop()
            return token
        } finally {
            lock.unlock()
        }
    }

    @Throws(InterruptedException::class)
    fun waitForToken(): Token? {
        return waitForToken { done.await() }
    }

    @Throws(InterruptedException::class)
    fun waitForToken(timeout: Long, unit: TimeUnit): Token? {
        return waitForToken { done.await(timeout, unit) }
    }

    private inner class Callback : AbstractHandler() {

        @Throws(IOException::class)
        override fun handle(target: String, request: HttpServletRequest,
            response: HttpServletResponse,
            dispatch: Int) {
            logger.debug("Handle...")
            if (CALLBACK_PATH != target) {
                logger.warn { "Wrong path: $target" }
                return
            }

            if (request.parameterMap.containsKey(ACCESS_TOKEN_KEY)) {
                if (state != request.getParameter(STATE_KEY)) {
                    logger.warn { "Wrong state: " + request.getParameter(STATE_KEY) }
                    return
                }
                logger.debug("Writing landing page...")
                writeLandingHtml(response)
                response.flushBuffer()
                lock.lock()
                try {
                    received = true
                    val accessToken = request.getParameter(ACCESS_TOKEN_KEY)
                    val expiresIn = request.getParameter(EXPIRATION_KEY)
                    val expiration = Integer.parseUnsignedInt(expiresIn).toLong()
                    val expirationDate = Instant.now().plusSeconds(expiration)
                    token = Token(accessToken, expirationDate)
                    done.signalAll()
                } catch (e: Exception) {
                    throw IOException(e)
                } finally {
                    lock.unlock()
                }
            } else {
                logger.debug("Redirecting...")
                writeRedirectHtml(response)
                response.flushBuffer()
            }

            (request as Request).isHandled = true
        }

        @Throws(IOException::class)
        private fun writeLandingHtml(response: HttpServletResponse) {
            response.setStatus(HttpServletResponse.SC_OK)
            response.contentType = "text/html"

            val doc = response.writer
            doc.print(landingPage)
            doc.flush()
        }

        @Throws(IOException::class)
        private fun writeRedirectHtml(response: HttpServletResponse) {
            response.setStatus(HttpServletResponse.SC_OK)
            response.contentType = "text/html"

            val doc = response.writer
            doc.print(redirectPage)
            doc.flush()
        }
    }

    private companion object {
        private const val STATE_KEY = "state"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val EXPIRATION_KEY = "expires_in"

        private const val CALLBACK_PATH = "/Callback"
        private const val LOCALHOST = "localhost"

        private const val LANDING_PAGE_FILE = "LandingPage.html"
        private const val REDIRECT_PAGE_FILE = "RedirectPage.html"

        private fun loadHtml(file: String): String {
            return LocalServerReceiver::class.java
                .getResourceAsStream(file)
                .bufferedReader(StandardCharsets.UTF_8)
                .readText()
        }
    }
}
