package com.rainyseason.coc.backend.data.telegram.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Update(
    @Json(name = "update_id")
    val updateId: Long,
    @Json(name = "message")
    val message: Message?,
    @Json(name = "callback_query")
    val callbackQuery: CallbackQuery?,
)
