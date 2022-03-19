package com.rainyseason.coc.backend

import com.rainyseason.coc.backend.core.handleRoutineContext
import com.rainyseason.coc.backend.handler.BackupWatchlistHandler
import com.rainyseason.coc.backend.util.getLogger
import com.rainyseason.coc.backend.util.then
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.AuthenticationHandler
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import javax.inject.Inject

class MainVerticle @Inject constructor(
    private val backupWatchlistHandler: BackupWatchlistHandler,
    private val authenticationHandler: AuthenticationHandler,
    private val buildConfig: BuildConfig,
) : CoroutineVerticle() {

    private val log = getLogger<MainVerticle>()

    override suspend fun start() {
        super.start()
        val port = System.getenv("PORT").orEmpty().toIntOrNull() ?: 80
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)

        router.route("/backup/*").handler(authenticationHandler)
        router.route("/backup/*").handler(
            BodyHandler.create()
                .setBodyLimit(2L * 1024 * 1024)
                .then(buildConfig.isDebug) {
                    getLogger<BodyHandler>().debug("process body for /backup/*")
                }
        )
        router.post("/backup/watchlist").handler { context: RoutingContext ->
            handleRoutineContext(context, backupWatchlistHandler)
        }
        server.requestHandler(router).listen(port).await()
        log.debug("HTTP server started on port $port")
    }
}
