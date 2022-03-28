package com.rainyseason.coc.backend

import com.rainyseason.coc.backend.di.AppComponent
import com.rainyseason.coc.backend.util.getLogger
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val logger = getLogger<Main>()
        logger.debug("main ${args.toList()}")
        val component = AppComponent.create()
        val app = args.first { it.startsWith("app=") }
            .removePrefix("app=")
        logger.debug("start app: $app")
        runBlocking {
            when (app) {
                "api" -> launch {
                    component.vertx.deployVerticle(component.mainVerticle).await()
                }
                "price_alert" -> component.priceAlertController.start()
                else -> error("unknown app: $app")
            }
        }
    }
}
