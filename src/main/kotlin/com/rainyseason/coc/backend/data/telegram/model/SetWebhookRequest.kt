package com.rainyseason.coc.backend.data.telegram.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SetWebhookRequest(
    @Json(name = "url")
    val url: String,
)
