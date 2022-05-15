package com.rainyseason.coc.backend.data.onesignal.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class SendNotificationRequest(
    @Json(name = "app_id")
    val appId: String,
    @Json(name = "headings")
    val headings: Map<String, String>,
    @Json(name = "contents")
    val contents: Map<String, String>,
    @Json(name = "include_external_user_ids")
    val includeExternalUserIds: List<String>,
    @Json(name = "channel_for_external_user_ids")
    val channelForExternalUserIds: String = "push",
)
