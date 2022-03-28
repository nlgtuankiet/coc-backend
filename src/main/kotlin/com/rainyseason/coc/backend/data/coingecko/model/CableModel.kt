package com.rainyseason.coc.backend.data.coingecko.model

import com.rainyseason.coc.backend.data.RawJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * {"command":"subscribe","identifier":"{\"channel\":\"CEChannel\"}"}
 * {"command":"unsubscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"1364\"}"}
 *
 * @param command subscribe or  unsubscribe
 */
@JsonClass(generateAdapter = true)
data class CableCommand(
    val command: String,
    @RawJson
    val identifier: CableIdentifier,
)

/**
 * @param channel CEChannel or PChannel
 */
@JsonClass(generateAdapter = true)
data class CableIdentifier(
    val channel: String,
    @RawJson
    @Json(name = "m")
    val coinId: Int? = null,
)

/**
 *
 *
 * @param type welcome, ping, confirm_subscription, reject_subscription
 * @param message
 */
@JsonClass(generateAdapter = true)
data class CableMessage(
    @RawJson
    val identifier: CableIdentifier? = null,
    val type: String? = null,
    val message: CablePercentMessage? = null,
)

@JsonClass(generateAdapter = true)
data class CablePercentMessage(
    @Json(name = "c")
    val coinId: Int,
    @Json(name = "p")
    val percent: Double
)
