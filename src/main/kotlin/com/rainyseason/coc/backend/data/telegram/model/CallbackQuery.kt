package com.rainyseason.coc.backend.data.telegram.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CallbackQuery(
    @Json(name = "id")
    val id: String,
    @Json(name = "message")
    val message: Message?,
    @Json(name = "data")
    val data: String?,
)
