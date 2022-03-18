package com.rainyseason.coc.backend

import com.rainyseason.coc.backend.di.DaggerAppComponent
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.runBlocking

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val component = DaggerAppComponent.create()
        runBlocking {
            component.vertx.deployVerticle(component.mainVerticle).await()
        }
    }
}
