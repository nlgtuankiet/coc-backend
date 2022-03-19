package com.rainyseason.coc.backend

import com.rainyseason.coc.backend.di.AppComponent
import com.rainyseason.coc.backend.util.getLogger
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        getLogger<Main>().debug("main ${args.toList()}")
        val component = AppComponent.create()

        runBlocking {
            component.vertx.deployVerticle(component.mainVerticle).await()
        }
    }
}
