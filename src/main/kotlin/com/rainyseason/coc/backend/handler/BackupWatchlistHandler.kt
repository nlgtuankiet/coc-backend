package com.rainyseason.coc.backend.handler

import com.google.cloud.firestore.Firestore
import com.rainyseason.coc.backend.core.RoutingContextHandler
import com.rainyseason.coc.backend.util.await
import com.rainyseason.coc.backend.util.firebaseUid
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.await
import javax.inject.Inject

class BackupWatchlistHandler @Inject constructor(
    private val firestore: Firestore,
) : RoutingContextHandler {

    override suspend fun handle(context: RoutingContext) {
        val uid = context.user()?.firebaseUid
        require(!uid.isNullOrBlank()) { "Invalid firebase uid" }
        val body = requireNotNull(context.body) { "Missing body" }.toString()
        firestore.collection("backup_watchlist").document(uid)
            .set(
                mapOf(
                    "json" to body
                )
            )
            .await()
        context.response().end()
    }
}
