package com.rainyseason.coc.backend.data.telegram.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class InlineKeyboardButton(
    @Json(name = "text")
    val text: String,
    @Json(name = "callback_data")
    val callbackData: String?,
)
