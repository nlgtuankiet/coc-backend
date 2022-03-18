package com.rainyseason.coc.backend.core

import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.launch
import java.lang.Exception

interface RoutingContextHandler {
    suspend fun handle(context: RoutingContext)
}

fun CoroutineVerticle.handleRoutineContext(
    context: RoutingContext,
    handler: RoutingContextHandler
) {
    launch {
        try {
            handler.handle(context)
        } catch (exception: Exception) {
            context.fail(exception)
        }
    }
}
