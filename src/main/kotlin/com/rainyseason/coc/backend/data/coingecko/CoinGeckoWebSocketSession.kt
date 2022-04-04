package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.data.coingecko.model.CableCommand
import com.rainyseason.coc.backend.data.coingecko.model.CableIdentifier
import com.rainyseason.coc.backend.data.coingecko.model.CableMessage
import com.rainyseason.coc.backend.data.model.CoinId
import com.rainyseason.coc.backend.data.ws.CloseReason
import com.rainyseason.coc.backend.data.ws.OkHttpTextWebSocketSession
import com.rainyseason.coc.backend.price.alert.PriceAlert
import com.rainyseason.coc.backend.util.getLogger
import com.rainyseason.coc.backend.util.notNull
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.WebSocket
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    internal val scope = CoroutineScope(serialDispatcher + SupervisorJob())
    private val logger = getLogger<CoinGeckoWebSocketSession>()
    internal val subscribedCoin = mutableSetOf<CoinId>()
    internal val messageListeners = mutableSetOf<CableMessageListener>()
    internal val pendingSubscribeRequest = Channel<List<PriceAlert>>(Channel.CONFLATED)
    internal val subscribeCEChannelResult = CompletableDeferred<Unit>()
    private val messageAdapter = moshi.adapter(CableMessage::class.java)
    private val commandAdapter = moshi.adapter(CableCommand::class.java)

    abstract class CableMessageListener : Function1<CableMessage, Unit> {
        @Volatile
        internal var isActive = true

        override fun invoke(message: CableMessage) {
        }
    }

    val subscribeCoinsJobs = scope.launch {
        logger.debug("process coins job")
        for (coins in pendingSubscribeRequest) {
            logger.debug("process coins: $coins")
            subscribeInternal(coins = coins)
        }
    }

    val processMessageJob = scope.launch {
        logger.debug("process message job")
        val toRemove = mutableListOf<CableMessageListener>()
        for (message in incoming) {
            logger.debug("process message: $message")
            val cableMessage = decodeMessage(message)
            messageListeners.forEach { listener ->
                if (listener.isActive) {
                    listener.invoke(cableMessage)
                    if (!listener.isActive) {
                        toRemove.add(listener)
                    }
                } else {
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
            val listener = object : CableMessageListener() {
                override fun invoke(message: CableMessage) {
                    if (message == expectedMessage) {
                        cont.resume(Unit)
                        isActive = false
                    }
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

    override fun start() {
        super.start()
        subscribeCEChannel()
    }

    internal fun subscribeCEChannel(
        timeout: Long = 10000,
    ) {
        scope.launch {
            var success = false
            var error: Throwable? = null
            try {
                withTimeout(timeout) {
                    success = suspendCancellableCoroutine { cont ->
                        val listener = object : CableMessageListener() {
                            override fun invoke(message: CableMessage) {
                                if (message == CONFIRM_CE_MESSAGE) {
                                    isActive = false
                                    cont.resume(true)
                                }
                            }
                        }
                        scope.launch {
                            sendCommand(
                                CableCommand("subscribe", CableIdentifier("CEChannel"))
                            )
                            messageListeners.add(listener)
                        }
                        cont.invokeOnCancellation {
                            if (it != null) {
                                error = it
                            }
                            listener.isActive = false
                        }
                    }
                }
            } catch (ex: TimeoutCancellationException) {
                error = ex
            }

            if (!success) {
                subscribeCEChannelResult.completeExceptionally(
                    error ?: Exception("Unknown error")
                )
                closeSelf(
                    code = CloseReason.Codes.VIOLATED_POLICY.code,
                    reason = "Subscribe to CEChannel failed"
                )
            } else {
                subscribeCEChannelResult.complete(Unit)
            }
        }
    }

    companion object {
        private val CONFIRM_CE_MESSAGE = CableMessage(
            type = "confirm_subscription",
            identifier = CableIdentifier(channel = "CEChannel")
        )
    }
}
