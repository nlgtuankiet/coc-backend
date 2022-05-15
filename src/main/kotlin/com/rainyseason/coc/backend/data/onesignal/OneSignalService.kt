package com.rainyseason.coc.backend.data.onesignal

import com.rainyseason.coc.backend.data.onesignal.model.SendNotificationRequest
import com.rainyseason.coc.backend.data.onesignal.model.SendNotificationResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OneSignalService {

    @POST("notifications")
    suspend fun sendNotification(@Body request: SendNotificationRequest): SendNotificationResponse

    companion object {
        const val BASE_URL = "https://onesignal.com/api/v1/"
    }
}
