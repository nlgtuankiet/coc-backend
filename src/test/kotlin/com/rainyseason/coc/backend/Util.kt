package com.rainyseason.coc.backend

import kotlinx.coroutines.Deferred
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend inline fun <T> Deferred<T>.awaitExceptionOrNull(): Throwable? {
    return suspendCoroutine { cont ->
        invokeOnCompletion { cont.resume(it) }
    }
}
