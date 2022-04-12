package com.rainyseason.coc.backend

import com.rainyseason.coc.backend.util.notNull
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend inline fun <T> Deferred<T>.awaitExceptionOrNull(): Throwable? {
    return suspendCoroutine { cont ->
        invokeOnCompletion { cont.resume(it) }
    }
}

suspend fun <T> T.awaitState(
    timeout: Long = 5000,
    block: T.() -> Boolean,
) {
    withTimeoutOrNull(timeout) {
        while (!block.invoke(this@awaitState)) {
            yield()
        }
    }.notNull { "failed" }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> Channel<T>.take(limit: Int): List<T> {
    val result = mutableListOf<T>()
    while (result.size != limit) {
        result.add(this.receive())
    }
    return result
}
