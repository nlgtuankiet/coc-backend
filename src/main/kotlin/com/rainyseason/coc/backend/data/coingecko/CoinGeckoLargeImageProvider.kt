package com.rainyseason.coc.backend.data.coingecko

interface CoinGeckoLargeImageProvider {
    suspend fun getLargeImage(id: String): String
}
