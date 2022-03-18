package com.rainyseason.coc.backend.handler

import com.google.cloud.firestore.Firestore
import com.rainyseason.coc.backend.core.RoutingContextHandler
import com.rainyseason.coc.backend.util.await
import io.vertx.ext.web.RoutingContext
import java.util.UUID
import javax.inject.Inject

class BackupWatchlistHandler @Inject constructor(
    private val firestore: Firestore,
) : RoutingContextHandler {

    override suspend fun handle(context: RoutingContext) {
        println("ackup invoked")
        firestore.collection("backup_watchlist").document("watchlist_backup")
            .set(mapOf("name" to UUID.randomUUID().toString()))
            .await()

        context.response().end("watchlist handler!")
    }
}
