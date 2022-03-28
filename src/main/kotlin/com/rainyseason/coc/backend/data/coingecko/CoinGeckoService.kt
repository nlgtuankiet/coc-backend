package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.data.coingecko.model.CoinDetailResponse
import com.rainyseason.coc.backend.data.coingecko.model.MarketsResponseEntry
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinGeckoService : CoinGeckoLargeImageProvider {

    @GET("coins/markets")
    suspend fun getCoinMarkets(
        @Query("vs_currency") vsCurrency: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int = 1,
    ): List<MarketsResponseEntry>

    @GET("coins/{id}")
    suspend fun getCoinDetail(@Path("id") id: String): CoinDetailResponse

    override suspend fun getLargeImage(id: String): String {
        return getCoinDetail(id).image.large
    }

    companion object {
        const val BASE_URL = "https://api.coingecko.com/api/v3/"
    }
}
