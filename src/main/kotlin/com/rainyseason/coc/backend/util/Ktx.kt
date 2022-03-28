package com.rainyseason.coc.backend.util

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.common.util.concurrent.MoreExecutors
import io.vertx.core.Handler
import io.vertx.ext.auth.User
import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

typealias VertxFuture<T> = io.vertx.core.Future<T>

suspend fun <T> ApiFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        this@await.cancel(true)
    }
    ApiFutures.addCallback(
        this@await,
        object : ApiFutureCallback<T> {
            override fun onFailure(t: Throwable?) {
                continuation.resumeWithException(t ?: Exception("Unknown error"))
            }

            override fun onSuccess(result: T) {
                continuation.resume(result)
            }
        },
        MoreExecutors.directExecutor()
    )
}

var User.firebaseUid: String?
    get() = attributes().getString("firebase_uid")
    set(value) {
        attributes().put("firebase_uid", value)
    }

fun <T> ApiFuture<T>.asVertxFuture(): VertxFuture<T> {
    return VertxFuture.future { promise ->
        ApiFutures.addCallback(
            this@asVertxFuture,
            object : ApiFutureCallback<T> {
                override fun onFailure(t: Throwable) {
                    promise.fail(t)
                }

                override fun onSuccess(result: T) {
                    promise.complete(result)
                }
            },
            MoreExecutors.directExecutor()
        )
    }
}

inline fun <T> T?.notNull(lazyMessage: () -> Any): T {
    return checkNotNull(this, lazyMessage)
}

inline fun <reified T> getLogger(): Logger {
    return LogManager.getLogger(T::class.java)
}

/**
 * Execute something after
 */
fun <T> Handler<T>.then(
    shouldWrap: Boolean = true,
    block: Handler<T>
): Handler<T> {
    return if (shouldWrap) {
        return Handler {
            this@then.handle(it)
            block.handle(it)
        }
    } else {
        this@then
    }
}
