package com.rainyseason.coc.backend.di

import com.rainyseason.coc.backend.BuildConfig
import com.rainyseason.coc.backend.MainVerticle
import com.rainyseason.coc.backend.price.alert.PriceAlertController
import com.squareup.moshi.Moshi
import dagger.BindsInstance
import dagger.Component
import io.vertx.core.Vertx
import org.apache.commons.configuration2.ImmutableConfiguration
import javax.inject.Singleton

@Component(
    modules = [AppModule::class, AppBinding::class]
)
@Singleton
interface AppComponent {
    val mainVerticle: MainVerticle
    val vertx: Vertx
    val priceAlertController: PriceAlertController
    val moshi: Moshi
    val config: ImmutableConfiguration

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance buildConfig: BuildConfig,
        ): AppComponent
    }

    companion object {
        fun create(buildConfig: BuildConfig): AppComponent {
            return DaggerAppComponent.factory().create(buildConfig)
        }
    }
}
