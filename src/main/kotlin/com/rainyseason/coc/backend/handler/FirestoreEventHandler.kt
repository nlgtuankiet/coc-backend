package com.rainyseason.coc.backend.handler

import com.rainyseason.coc.backend.core.ConfigKeys
import com.rainyseason.coc.backend.core.RoutingContextHandler
import com.rainyseason.coc.backend.core.getValue
import com.rainyseason.coc.backend.data.firebase.FirestoreEvent
import com.rainyseason.coc.backend.data.onesignal.OneSignalService
import com.rainyseason.coc.backend.data.onesignal.model.SendNotificationRequest
import com.rainyseason.coc.backend.data.telegram.TelegramService
import com.rainyseason.coc.backend.data.telegram.model.Message
import com.rainyseason.coc.backend.util.getLogger
import com.rainyseason.coc.backend.util.notNull
import com.squareup.moshi.Moshi
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.await
import org.apache.commons.configuration2.ImmutableConfiguration
import javax.inject.Inject

class FirestoreEventHandler @Inject constructor(
    moshi: Moshi,
    private val telegramService: TelegramService,
    private val oneSignalService: OneSignalService,
    private val config: ImmutableConfiguration,
) : RoutingContextHandler {
    private val log = getLogger<FirestoreEventHandler>()
    private val firestoreEventAdapter = moshi.adapter(FirestoreEvent::class.java)
    // chats/4OMuY4qmIoUZpPKIRdQXeTnAVjg1_uJIQWCKAsLMf3AlY1bTjVhmJlVM2/messages/HiqB5iATifPflyzE1pzh
    private val messagePathRegex = """^chats/(\w+)_(\w+)/messages/(\w+)${'$'}""".toRegex()
    private val telegramBotAndAdminChatId = config.getValue(ConfigKeys.TelegramBotAndAdminChatId)
    private val firebaseAdminUid = config.getValue(ConfigKeys.FirebaseAdminUid)
    private val oneSignalAppId = config.getValue(ConfigKeys.oneSignalAppId)

    override suspend fun handle(context: RoutingContext) {
        val jsonBody = requireNotNull(context.body) { "Missing body" }.toString()
        log.debug("request json: $jsonBody")
        val event = firestoreEventAdapter.fromJson(jsonBody)
            .notNull { "fail to convert json: ${context.body}" }
        log.debug(event)
        when {
            messagePathRegex.matches(event.refPath) -> onMessageCreate(context, event)
        }
        context.response().setStatusCode(204).end().await()
    }

    private suspend fun onMessageCreate(context: RoutingContext, event: FirestoreEvent) {
        log.debug("onMessageCreate, $event")
        val senderUid = event.data.getValue("sender_uid") as String
        if (senderUid == firebaseAdminUid) {
            sendNotificationToUser(context, event)
            return
        }
        val text = event.data.getValue("text") as String

        // make telegram bot send message to admin
        val messageRequest = Message(
            chatId = telegramBotAndAdminChatId,
            text = "uid: $senderUid, text: $text",
        ).withUtilReplyMarkup(userUid = senderUid)
        telegramService.sendMessage(messageRequest)
    }

    private suspend fun sendNotificationToUser(
        context: RoutingContext,
        event: FirestoreEvent
    ) {
        val refPath = event.refPath
        val matchResult = messagePathRegex.matchEntire(refPath) ?: return
        val user1Uid = matchResult.groupValues[1]
        val user2Uid = matchResult.groupValues[2]
        val userUid = if (user1Uid == firebaseAdminUid) {
            user2Uid
        } else {
            user1Uid
        }
        val text = event.data["text"] as? String ?: return
        val result = oneSignalService.sendNotification(
            SendNotificationRequest(
                appId = oneSignalAppId,
                headings = mapOf(
                    "en" to "Customer Support",
                ),
                contents = mapOf(
                    "en" to text,
                ),
                includeExternalUserIds = listOf(userUid)
            )
        )
        require(result.recipients == 1L)
    }
}
