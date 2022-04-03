package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.data.coingecko.model.CableCommand
import com.rainyseason.coc.backend.data.coingecko.model.CableIdentifier
import com.rainyseason.coc.backend.data.coingecko.model.CableMessage
import com.rainyseason.coc.backend.data.model.CoinId
import com.rainyseason.coc.backend.data.ws.OkHttpTextWebSocketSession
import com.rainyseason.coc.backend.price.alert.PriceAlert
import com.rainyseason.coc.backend.util.getLogger
import com.rainyseason.coc.backend.util.notNull
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.WebSocket
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private typealias CacheMessageListener = (CableMessage) -> Boolean

@OptIn(ExperimentalCoroutinesApi::class)
class CoinGeckoWebSocketSession constructor(
    private val moshi: Moshi,
    private val coinGeckoIdResolver: CoinGeckoIdResolver,
    webSocketFactory: WebSocket.Factory,
    request: Request = Request.Builder()
        .url("wss://cables.coingecko.com/cable")
        .header("Origin", "https://www.coingecko.com")
        .build(),
    coroutineText: CoroutineContext = Dispatchers.IO.limitedParallelism(1),
) : OkHttpTextWebSocketSession(
    webSocketFactory = webSocketFactory,
    request = request,
    coroutineText = coroutineText
) {
    private val serialDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(serialDispatcher + SupervisorJob())
    private val logger = getLogger<CoinGeckoWebSocketSession>()
    internal val subscribedCoin = mutableSetOf<CoinId>()
    internal val messageListeners = mutableSetOf<CacheMessageListener>()
    internal val pendingSubscribeRequest = Channel<List<PriceAlert>>(Channel.CONFLATED)
    private val messageAdapter = moshi.adapter(CableMessage::class.java)
    private val commandAdapter = moshi.adapter(CableCommand::class.java)

    val subscribeCoinsJobs = scope.launch {
        logger.debug("process coins job")
        for (coins in pendingSubscribeRequest) {
            logger.debug("process coins: $coins")
            subscribeInternal(coins = coins)
        }
    }

    val processMessageJob = scope.launch {
        logger.debug("process message job")
        val toRemove = mutableListOf<CacheMessageListener>()
        for (message in incoming) {
            logger.debug("process message: $message")
            val cableMessage = decodeMessage(message)
            messageListeners.forEach { listener ->
                val shouldRemove = listener.invoke(cableMessage)
                if (shouldRemove) {
                    toRemove.add(listener)
                }
            }

            if (toRemove.isNotEmpty()) {
                messageListeners.removeAll(toRemove)
                toRemove.clear()
            }
        }
    }

    var outgoingOverride: SendChannel<String>? = null

    override val outgoing: SendChannel<String>
        get() = outgoingOverride ?: super.outgoing

    private fun decodeMessage(json: String): CableMessage {
        return messageAdapter.fromJson(json).notNull {
            "Unknown message: $json"
        }
    }

    private fun sendCommand(command: CableCommand) {
        val json: String = commandAdapter.toJson(command)
        outgoing.trySend(json)
    }

    fun subscribe(coins: List<PriceAlert>) {
        logger.debug("subscribe $coins")
        pendingSubscribeRequest.trySend(coins)
    }

    private suspend fun subscribeInternal(coins: List<PriceAlert>) {
        val set = coins.map { it.coinId }.toSet()
        val toSubscribe = set.filter { it !in subscribedCoin }
        val toUnsubscribe = subscribedCoin.filter { it !in set }
        coroutineScope {
            toSubscribe.forEach { launch { subscribe(it) } }
            toUnsubscribe.forEach { launch { unsubscribe(it) } }
        }
        subscribedCoin.clear()
        subscribedCoin.addAll(set)
        logger.debug("subscribe $coins done")
    }

    private suspend fun subscribe(coin: CoinId) {
        logger.debug("subscribe coin: $coin")
        val coinId = coinGeckoIdResolver.resolve(coin.id)
        val identifier = CableIdentifier(
            channel = "PChannel",
            coinId = coinId,
        )
        val command = CableCommand(
            identifier = identifier,
            command = "subscribe",

        )
        val expectedMessage = CableMessage(
            identifier = identifier,
            type = "confirm_subscription"
        )

        suspendCoroutine<Unit> { cont ->
            val listener: CacheMessageListener = { message ->
                if (message == expectedMessage) {
                    cont.resume(Unit)
                    true
                } else {
                    false
                }
            }
            scope.launch {
                sendCommand(command)
                messageListeners.add(listener)
            }
        }
    }

    private suspend fun unsubscribe(coin: CoinId) {
        logger.debug("unsubscribe $coin")
        val coinId = coinGeckoIdResolver.resolve(coin.id)
        val command = CableCommand(
            command = "unsubscribe",
            identifier = CableIdentifier(
                channel = "PChannel",
                coinId = coinId
            )
        )
        sendCommand(command)
    }
}
