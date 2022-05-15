package com.rainyseason.coc.backend

import com.rainyseason.coc.backend.core.ConfigKeys
import com.rainyseason.coc.backend.core.getValue
import com.rainyseason.coc.backend.core.handleRoutineContext
import com.rainyseason.coc.backend.handler.BackupWatchlistHandler
import com.rainyseason.coc.backend.handler.FirestoreEventHandler
import com.rainyseason.coc.backend.handler.TelegramUpdateHandler
import com.rainyseason.coc.backend.util.getLogger
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.AuthenticationHandler
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.apache.commons.configuration2.ImmutableConfiguration
import javax.inject.Inject

class MainVerticle @Inject constructor(
    private val backupWatchlistHandler: BackupWatchlistHandler,
    private val firestoreEventHandler: FirestoreEventHandler,
    private val authenticationHandler: AuthenticationHandler,
    private val telegramUpdateHandler: TelegramUpdateHandler,
    private val buildConfig: BuildConfig,
    private val configuration: ImmutableConfiguration,
) : CoroutineVerticle() {

    private val log = getLogger<MainVerticle>()
    private val firestoreForwardPassword = configuration.getValue(ConfigKeys.FirestoreForwardPassword)
    private val telegramBotToken = configuration.getValue(ConfigKeys.TelegramBotToken)

    override suspend fun start() {
        super.start()
        val port = System.getenv("PORT").orEmpty().toIntOrNull() ?: 80
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)
        if (buildConfig.isDebug) {
            configuration.keys.forEach {
                println("config $it -> ${configuration.getString(it)}")
            }
            router.route().apply {
                handler {
                    log.debug("request url: ${it.request().uri()}")
                    it.next()
                }
            }
        }

        router.post("/backup/watchlist").apply {
            handler(backupWatchlistHandler.validationHandler)
            handler(authenticationHandler)
            handleBody(100 * 1024)
            handler { context: RoutingContext ->
                handleRoutineContext(context, backupWatchlistHandler)
            }
        }

        router.post("/firestoreEvent/$firestoreForwardPassword")
            .apply {
                handleBody(1 * 1024 * 1024)
                handler { context: RoutingContext ->
                    handleRoutineContext(context, firestoreEventHandler)
                }
            }

        router.post("/telegramHook/$telegramBotToken").apply {
            handleBody(2 * 1024 * 1024)
            handler {
                handleRoutineContext(it, telegramUpdateHandler)
            }
        }

        server.requestHandler(router).listen(port).await()
        log.debug("HTTP server started on port $port")
    }

    private fun Route.handleBody(size: Long) {
        handler(BodyHandler.create().setBodyLimit(size))
    }
}
