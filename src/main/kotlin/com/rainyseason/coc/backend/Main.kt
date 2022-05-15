package com.rainyseason.coc.backend

import com.rainyseason.coc.backend.core.ConfigKeys
import com.rainyseason.coc.backend.di.AppComponent
import com.rainyseason.coc.backend.util.Env
import com.rainyseason.coc.backend.util.getLogger
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val logger = getLogger<Main>()
        logger.debug("main ${args.toList()}")
        val app = args.first { it.startsWith("app=") }.removePrefix("app=")
        logger.debug("start app: $app")

        val debug = args.firstOrNull { it.startsWith("debug=") }
            ?.removePrefix("debug=")
            ?.toBooleanStrictOrNull() ?: false

        val httpLog = args.firstOrNull { it.startsWith("httpLog=") }
            ?.removePrefix("httpLog=")

        val buildConfig = BuildConfig(
            isDebug = debug,
            httpLog = httpLog,
        )
        logger.debug("build config: $buildConfig")

        val component = AppComponent.create(buildConfig)

        Env.CONFIG_FILE_PATH // check env
        ConfigKeys.checkAll(component.config) // check config
        runBlocking {
            when (app) {
                "api" -> {
                    launch {
                        component.vertx.deployVerticle(component.mainVerticle).await()
                    }
                }
                "price_alert" -> component.priceAlertController.start()
                else -> error("unknown app: $app")
            }
        }
    }
}
