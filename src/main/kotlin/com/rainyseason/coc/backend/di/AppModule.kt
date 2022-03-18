package com.rainyseason.coc.backend.di

import dagger.Module
import dagger.Provides
import io.vertx.core.Vertx
import javax.inject.Singleton

@Module
object AppModule {

    @Provides
    @Singleton
    fun vertx(): Vertx {
        return Vertx.vertx()
    }
}
