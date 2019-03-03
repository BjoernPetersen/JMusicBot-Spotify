package net.bjoernpetersen.spotify.auth

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import kotlin.concurrent.withLock

@Suppress("DEPRECATION")
@ExtendWith(VertxExtension::class, PortExtension::class)
@Execution(ExecutionMode.CONCURRENT)
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class CallbackVerticleTest {

    private fun send(
        vertx: Vertx,
        port: Int,
        state: String? = null,
        token: String? = null,
        expirationTime: Int? = null,
        handler: (HttpClientResponse) -> Unit
    ) {
        val queryParams: MutableList<Pair<String, String>> = LinkedList()
        if (state != null) {
            queryParams.add(CallbackVerticle.STATE_KEY to state)
        }
        if (token != null) {
            queryParams.add(CallbackVerticle.ACCESS_TOKEN_KEY to token)
        }
        if (expirationTime != null) {
            queryParams.add(CallbackVerticle.EXPIRATION_KEY to expirationTime.toString())
        }
        val queryString = if (queryParams.isEmpty()) "" else queryParams
            .joinToString(prefix = "?", separator = "&") { "${it.first}=${it.second}" }

        val client = vertx.createHttpClient()
        client.request(
            HttpMethod.GET,
            port,
            CallbackVerticle.LOCALHOST,
            "${CallbackVerticle.REDIRECT_PATH}$queryString"
        ) {
            handler(it)
        }.end()
        client.close()
    }

    private fun test(
        vertx: Vertx,
        port: Int,
        shouldReturn: Boolean,
        shouldSucceed: Boolean,
        code: Int,
        state: String? = null,
        token: String? = null,
        expirationTime: Int? = null
    ) {
        val lock = ReentrantLock()
        val done = lock.newCondition()
        lock.withLock {
            CallbackVerticle.start(port, CORRECT_STATE) {
                assertEquals(shouldSucceed, it.succeeded())
                if (shouldSucceed)
                    assertEquals(VALID_TOKEN, it.result().value) {
                        "Token was not extracted correctly"
                    }
                lock.withLock { done.signal() }
            }

            send(
                vertx, port,
                state = state, token = token, expirationTime = expirationTime
            ) {
                assertEquals(code, it.statusCode())
            }

            assertEquals(shouldReturn, done.await(16, TimeUnit.SECONDS))
        }
    }

    @Test
    fun success(vertx: Vertx, port: Int) {
        test(vertx, port, true, true, 200, CORRECT_STATE, VALID_TOKEN, 1800)
    }

    @TestFactory
    fun invalidState(vertx: Vertx, portSupplier: Supplier<Int>): List<DynamicTest> {
        return listOf(null, VALID_TOKEN)
            .flatMap { token -> listOf(null, 1800).map { token to it } }
            .flatMap { (token, expirationTime) ->
                listOf(null, WRONG_STATE).map { Triple(token, expirationTime, it) }
            }
            .map { (token, expirationTime, state) ->
                dynamicTest("token: $token, expirationTime: $expirationTime, state: $state") {
                    test(
                        vertx, portSupplier.get(),
                        false, false, 403, state, token, expirationTime
                    )
                }
            }
    }

    @Test
    fun noToken(vertx: Vertx, port: Int) {
        test(vertx, port, true, false, 401, CORRECT_STATE)
    }

    private companion object {
        const val CORRECT_STATE = "fsgd8769345khj"
        const val WRONG_STATE = "dsfkhjl34587"

        const val VALID_TOKEN = "kdjfhg324ljk"
    }
}
