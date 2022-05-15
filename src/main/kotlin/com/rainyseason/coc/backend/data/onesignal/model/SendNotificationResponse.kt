package com.rainyseason.coc.backend.data.onesignal.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class SendNotificationResponse(
    @Json(name = "id")
    val id: String,
    @Json(name = "recipients")
    val recipients: Long
)
