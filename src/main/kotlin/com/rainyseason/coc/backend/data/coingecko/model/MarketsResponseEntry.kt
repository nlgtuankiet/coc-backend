package com.rainyseason.coc.backend.data.coingecko.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarketsResponseEntry(
    @Json(name = "id")
    val id: String,

    @Json(name = "image")
    val image: String,
)
