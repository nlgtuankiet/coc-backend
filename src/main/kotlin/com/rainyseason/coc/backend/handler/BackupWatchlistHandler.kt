package com.rainyseason.coc.backend.handler

import com.rainyseason.coc.backend.core.RoutingContextHandler
import io.vertx.ext.web.RoutingContext
import javax.inject.Inject

class BackupWatchlistHandler @Inject constructor(): RoutingContextHandler {

    override suspend fun handle(context: RoutingContext) {
        context.response().end("watchlist handler!")
    }
}
