package com.rainyseason.coc.backend.handler

import com.rainyseason.coc.backend.core.ConfigKeys
import com.rainyseason.coc.backend.core.RoutingContextHandler
import com.rainyseason.coc.backend.core.getValue
import com.rainyseason.coc.backend.data.firebase.FirestoreEvent
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
    private val config: ImmutableConfiguration,
) : RoutingContextHandler {
    private val log = getLogger<FirestoreEventHandler>()
    private val firestoreEventAdapter = moshi.adapter(FirestoreEvent::class.java)
    // chats/4OMuY4qmIoUZpPKIRdQXeTnAVjg1_uJIQWCKAsLMf3AlY1bTjVhmJlVM2/messages/HiqB5iATifPflyzE1pzh
    private val messagePathRegex = """^chats/(\w+)_(\w+)/messages/(\w+)${'$'}""".toRegex()
    private val telegramBotAndAdminChatId = config.getValue(ConfigKeys.TelegramBotAndAdminChatId)
    private val firebaseAdminUid = config.getValue(ConfigKeys.FirebaseAdminUid)

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
            return // do nothing
        }
        val text = event.data.getValue("text") as String

        // make telegram bot send message to admin
        val messageRequest = Message(
            chatId = telegramBotAndAdminChatId,
            text = "from uid: $senderUid, text: $text",
        ).withUtilReplyMarkup(userUid = senderUid)
        telegramService.sendMessage(messageRequest)
    }
}
