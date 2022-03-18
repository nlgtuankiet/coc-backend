package com.rainyseason.coc.backend.di

import com.rainyseason.coc.backend.MainVerticle
import dagger.Component
import io.vertx.core.Vertx
import javax.inject.Singleton

@Component(
    modules = [AppModule::class]
)
@Singleton
interface AppComponent {
    val mainVerticle: MainVerticle
    val vertx: Vertx
}
