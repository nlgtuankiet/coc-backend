package com.rainyseason.coc.backend

import com.rainyseason.coc.backend.core.handleRoutineContext
import com.rainyseason.coc.backend.handler.BackupWatchlistHandler
import io.vertx.core.impl.launcher.VertxCommandLauncher
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import javax.inject.Inject


class MainVerticle @Inject constructor(
    private val backupWatchlistHandler: BackupWatchlistHandler,
) : CoroutineVerticle() {

    override suspend fun start() {
        super.start()
        val port = System.getenv("PORT").orEmpty().toIntOrNull() ?: 80
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)
        router.get("/backup/watchlist").handler { context: RoutingContext ->
            handleRoutineContext(context, backupWatchlistHandler)
        }
        server.requestHandler(router).listen(port).await()
        println("HTTP server started on port $port")
    }

}
