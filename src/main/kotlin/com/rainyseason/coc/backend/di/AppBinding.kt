package com.rainyseason.coc.backend.di

import com.rainyseason.coc.backend.data.coingecko.CoinGeckoIdResolver
import com.rainyseason.coc.backend.data.coingecko.CoinGeckoIdResolverImpl
import com.rainyseason.coc.backend.data.coingecko.CoinGeckoLargeImageProvider
import com.rainyseason.coc.backend.data.coingecko.CoinGeckoService
import com.rainyseason.coc.backend.data.coingecko.CoinGeckoWebSocketSession
import com.rainyseason.coc.backend.data.coingecko.CoinGeckoWebSocketSessionImpl
import dagger.Binds
import dagger.Module

@Module
interface AppBinding {
    @Binds
    fun coinGeckoLargeImageProvider(impl: CoinGeckoService): CoinGeckoLargeImageProvider

    @Binds
    fun coinGeckoIdResolver(impl: CoinGeckoIdResolverImpl): CoinGeckoIdResolver

    @Binds
    fun coinGeckoWebSocketSessionFactory(
        impl: CoinGeckoWebSocketSessionImpl.Factory,
    ): CoinGeckoWebSocketSession.Factory
}
