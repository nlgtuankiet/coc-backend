package com.rainyseason.coc.backend.data.telegram.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Message(
    @Json(name = "chat_id")
    val chatId: Long?,
    @Json(name = "text")
    val text: String? = null,
    @Json(name = "reply_markup")
    val replyMarkup: InlineKeyboardMarkup? = null,
    @Json(name = "parse_mode")
    val parseMode: String? = null,
    @Json(name = "reply_to_message")
    val replyToMessage: Message? = null
)

fun Message.withMarkdownV2ParseMode(): Message = copy(parseMode = "MarkdownV2")
