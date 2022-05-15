package com.rainyseason.coc.backend.data.telegram

import com.rainyseason.coc.backend.data.telegram.model.Message
import com.rainyseason.coc.backend.data.telegram.model.SetWebhookRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface TelegramService {
    @POST("sendMessage")
    suspend fun sendMessage(@Body message: Message)

    @POST("setWebhook")
    suspend fun setWebhook(@Body request: SetWebhookRequest)
}
