package com.rainyseason.coc.backend.handler

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.rainyseason.coc.backend.core.ConfigKeys
import com.rainyseason.coc.backend.core.RoutingContextHandler
import com.rainyseason.coc.backend.core.getValue
import com.rainyseason.coc.backend.data.telegram.TelegramService
import com.rainyseason.coc.backend.data.telegram.model.CallbackQuery
import com.rainyseason.coc.backend.data.telegram.model.InlineKeyboardButton
import com.rainyseason.coc.backend.data.telegram.model.InlineKeyboardMarkup
import com.rainyseason.coc.backend.data.telegram.model.Message
import com.rainyseason.coc.backend.data.telegram.model.SetWebhookRequest
import com.rainyseason.coc.backend.data.telegram.model.Update
import com.rainyseason.coc.backend.data.telegram.model.withMarkdownV2ParseMode
import com.rainyseason.coc.backend.util.ChatUtil
import com.rainyseason.coc.backend.util.asInstant
import com.rainyseason.coc.backend.util.await
import com.rainyseason.coc.backend.util.escapedMarkdown
import com.rainyseason.coc.backend.util.getLogger
import com.rainyseason.coc.backend.util.notNull
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.commons.configuration2.ImmutableConfiguration
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramUpdateHandler @Inject constructor(
    private val telegramService: TelegramService,
    private val config: ImmutableConfiguration,
    private val appScope: CoroutineScope,
    private val moshi: Moshi,
    private val firestore: Firestore,
) : RoutingContextHandler {
    private val log = getLogger<TelegramUpdateHandler>()
    private val updateAdapter: JsonAdapter<Update> = moshi.adapter(Update::class.java)
    private val getHistoryDataRegex = """^(\w+)_h(\d+)${'$'}""".toRegex()
    private val messageTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")
            .withZone(ZoneId.of("+7"))
    private val telegramBotAndAdminChatId = config.getValue(ConfigKeys.TelegramBotAndAdminChatId)
    private val host = config.getValue(ConfigKeys.Host)
    private val telegramBotToken = config.getValue(ConfigKeys.TelegramBotToken)
    private val firebaseAdminUid = config.getValue(ConfigKeys.FirebaseAdminUid)

    init {
        appScope.launch(Dispatchers.IO) {
            // register hook
            telegramService.setWebhook(
                SetWebhookRequest(
                    url = "${host}telegramHook/$telegramBotToken"
                )
            )
            log.debug("set webhook success")
        }
    }

    override suspend fun handle(context: RoutingContext) {
        log.debug("body: ${context.body}")
        val update = updateAdapter.fromJson(context.body.toString())
            .notNull { "json convert fail ${context.body}" }
        context.response().setStatusCode(204).end().await()
        when {
            update.callbackQuery != null -> handleCallback(context, update.callbackQuery)
            update.message?.replyToMessage != null ->
                handleReplyMessage(context, update.message, update.message.replyToMessage)
        }
    }

    private suspend fun handleReplyMessage(
        context: RoutingContext,
        message: Message,
        replyToMessage: Message,
    ) {
        val uid = replyToMessage.replyMarkup?.inlineKeyboard?.flatten()
            .orEmpty()
            .firstNotNullOfOrNull { button ->
                button.callbackData?.let { data ->
                    // look for uid in 4OMuY4qmIoUZpPKIRdQXeTnAVjg1_h25
                    getHistoryDataRegex.matchEntire(data)?.groupValues?.getOrNull(1)
                }
            } ?: return

        if (uid.isBlank()) {
            return
        }

        val text = message.text
        if (text.isNullOrBlank()) {
            return
        }

        // send message to chat
        val chatId = ChatUtil.getChatId(uid, firebaseAdminUid)

        val chatDocument = firestore.collection("chats")
            .document(chatId)

        val messageDocument = chatDocument
            .collection("messages")
            .document()

        val docData = mapOf(
            "create_at" to FieldValue.serverTimestamp(),
            "message_id" to messageDocument.id,
            "sender_uid" to firebaseAdminUid,
            "text" to text
        )

        coroutineScope {
            val task1 = async {
                chatDocument.set(docData).await()
            }
            val task2 = async {
                messageDocument.set(docData).await()
            }
            listOf(task1, task2).awaitAll()
        }
    }

    private suspend fun handleCallback(context: RoutingContext, callbackQuery: CallbackQuery) {
        val data = callbackQuery.data
        if (data.isNullOrBlank()) {
            return
        }

        val historyMatch = getHistoryDataRegex.matchEntire(data)
        if (historyMatch != null) {
            val uid = historyMatch.groupValues[1]
            val max = historyMatch.groupValues[2].toInt()
            sendHistoryMessage(context, uid, max)
            return
        }
    }

    private suspend fun sendHistoryMessage(context: RoutingContext, uid: String, max: Int) {
        if (max > 50) {
            return
        }
        val snapshot = firestore.collection("chats")
            .document(ChatUtil.getChatId(uid, firebaseAdminUid))
            .collection("messages")
            .orderBy("create_at", Query.Direction.DESCENDING)
            .limit(max)
            .get().await()
        val historyMessage = buildString {
            // title
            append("`")
            append("History of ${uid.takeLast(7)}")
            append("`")
            appendLine()

            for (document in snapshot.documents.asReversed()) {
                val createAt = document.getTimestamp("create_at")?.asInstant() ?: continue
                val senderUid = document.getString("sender_uid") ?: continue
                val text = document.getString("text").orEmpty()
                val isFromAdmin = senderUid == firebaseAdminUid
                // chat
                // 05/13 14:10:44 👤 content of message from user
                // 05/13 14:11:14 😎 content of message from admin
                append("`")
                append(messageTimeFormatter.format(createAt).escapedMarkdown())
                append("`")

                append(" ")
                if (isFromAdmin) {
                    append("😎".escapedMarkdown())
                } else {
                    append("👤".escapedMarkdown())
                }

                append(" ")
                append(text.escapedMarkdown())
                appendLine()
            }
        }
        val message = Message(
            chatId = telegramBotAndAdminChatId,
            text = historyMessage
        ).withUtilReplyMarkup(userUid = uid)
            .withMarkdownV2ParseMode()
        telegramService.sendMessage(message)
    }
}

fun Message.withUtilReplyMarkup(
    userUid: String,
): Message {
    return copy(
        replyMarkup = InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton(
                        text = "History 10",
                        callbackData = "${userUid}_h10",
                    ),
                    InlineKeyboardButton(
                        text = "History 25",
                        callbackData = "${userUid}_h25",
                    ),
                )
            )
        )
    )
}
