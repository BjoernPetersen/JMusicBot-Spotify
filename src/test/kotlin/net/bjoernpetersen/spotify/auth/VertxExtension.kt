package net.bjoernpetersen.spotify.auth

import io.vertx.core.Vertx
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class VertxExtension : Extension, ParameterResolver, BeforeAllCallback, AfterAllCallback {
    private lateinit var vertx: Vertx

    override fun beforeAll(context: ExtensionContext) {
        vertx=Vertx.vertx()
    }

    override fun afterAll(context: ExtensionContext) {
        val lock = ReentrantLock()
        val done = lock.newCondition()
        lock.withLock {
            vertx.close {
                lock.withLock { done.signal() }
            }
            done.await()
        }
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        return Vertx::class.java.isAssignableFrom(parameterContext.parameter.type)
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        return vertx
    }


}
