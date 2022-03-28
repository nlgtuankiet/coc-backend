package com.rainyseason.coc.backend.data.coingecko.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CoinDetailResponse(
    @Json(name = "id")
    val id: String,

    @Json(name = "image")
    val image: Image,
) {

    @JsonClass(generateAdapter = true)
    data class Image(
        @Json(name = "large")
        val large: String
    )
}
