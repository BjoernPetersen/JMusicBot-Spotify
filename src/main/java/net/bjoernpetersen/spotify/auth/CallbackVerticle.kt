package net.bjoernpetersen.spotify.auth

import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging
import java.net.URL
import java.time.Instant

internal class CallbackVerticle private constructor(
    private val port: Int,
    private val state: String,
    private val future: Future<Token>
) : AbstractVerticle() {

    private val redirectPageContent = loadHtml(REDIRECT_PAGE_FILE)

    override fun start(startFuture: Future<Void>) {
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)

        router.route(HttpMethod.GET, CALLBACK_PATH).handler(::callback)
        router.route(HttpMethod.GET, REDIRECT_PATH).handler(::redirect)

        server.requestHandler(router).listen(port, LOCALHOST) {
            if (it.succeeded()) startFuture.complete()
            else startFuture.fail(it.cause())
        }
    }

    private fun callback(ctx: RoutingContext) {
        ctx.response().end(redirectPageContent)
    }

    private fun redirect(ctx: RoutingContext) {
        ctx.vertx().executeBlocking({ future: Future<Token> ->
            try {
                if (ctx.queryParam(STATE_KEY).firstOrNull() != this.state)
                    throw IllegalArgumentException()

                val token = ctx.queryParam(ACCESS_TOKEN_KEY).first()
                val expiresIn = ctx.queryParam(EXPIRATION_KEY).first()
                val expiration = Integer.parseUnsignedInt(expiresIn).toLong()
                val expirationDate = Instant.now().plusSeconds(expiration)
                future.complete(Token(token, expirationDate))
            } catch (e: Exception) {
                future.fail(e)
            }
        }, {
            if (it.succeeded()) {
                ctx.response().end("Successfully received token. You may close this window now.")
                future.complete(it.result())
            } else {
                val cause = it.cause()
                when (cause) {
                    is IllegalArgumentException ->
                        ctx.response().setStatusCode(403).end("Invalid state")
                    else -> {
                        ctx.response().setStatusCode(401).end("Did not receive a valid token.")
                        future.fail(cause)
                    }
                }
            }
        })
    }

    companion object {
        const val STATE_KEY = "state"
        const val ACCESS_TOKEN_KEY = "access_token"
        const val EXPIRATION_KEY = "expires_in"

        private const val CALLBACK_PATH = "/Callback"
        const val REDIRECT_PATH = "/redirect"
        const val LOCALHOST = "localhost"

        private const val REDIRECT_PAGE_FILE = "RedirectPage.html"

        private val logger = KotlinLogging.logger { }

        private fun loadHtml(fileName: String): String {
            return this::class.java
                .getResourceAsStream(fileName)
                .bufferedReader()
                .readText()
        }

        fun start(port: Int, state: String, handler: (AsyncResult<Token>) -> Unit): URL {
            val future = Future.future<Token>()
            val vertx = Vertx.vertx()

            future.setHandler {
                vertx.executeBlocking({ future: Future<Unit> ->
                    future.complete(handler(it))
                }, {
                    if (it.succeeded()) {
                        logger.debug { "Handler succeeded" }
                    } else {
                        logger.error(it.cause()) {}
                    }
                    vertx.close()
                })
            }

            val verticle = CallbackVerticle(port, state, future)
            vertx.deployVerticle(verticle) {
                if (it.succeeded()) {
                    vertx.setTimer(15000) {
                        vertx.close()
                    }
                } else {
                    vertx.close()
                    handler(Future.failedFuture(it.cause()))
                }
            }
            return URL(
                "http",
                "localhost",
                port,
                CALLBACK_PATH
            )
        }
    }
}
