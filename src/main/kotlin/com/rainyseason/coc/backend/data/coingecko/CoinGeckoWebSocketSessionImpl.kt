package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.data.coingecko.model.CableCommand
import com.rainyseason.coc.backend.data.coingecko.model.CableIdentifier
import com.rainyseason.coc.backend.data.coingecko.model.CableMessage
import com.rainyseason.coc.backend.data.model.CoinId
import com.rainyseason.coc.backend.data.ws.CloseReason
import com.rainyseason.coc.backend.data.ws.OkHttpTextWebSocketSession
import com.rainyseason.coc.backend.util.getLogger
import com.rainyseason.coc.backend.util.notNull
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.WebSocket
import kotlin.coroutines.resume

interface CoinGeckoWebSocketSession {
    fun start()
    val closeReason: Deferred<CloseReason>
    fun subscribe(coins: Set<CoinId>): ChannelResult<Unit>

    interface Factory {
        fun create(
            webSocketFactory: WebSocket.Factory,
            dispatcher: CoroutineDispatcher,
            cableMessages: SendChannel<CableMessage>,
        ): CoinGeckoWebSocketSession
    }
}

/**
 * Wait for welcome message before sending any command
 * TODO log non fatal exception to logging system
 * TODO add test send cable message back to cableMessages
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoinGeckoWebSocketSessionImpl @AssistedInject constructor(
    @Assisted webSocketFactory: WebSocket.Factory,
    @Assisted dispatcher: CoroutineDispatcher,
    @Assisted outMessages: SendChannel<CableMessage>,
    moshi: Moshi,
    private val coinGeckoIdResolver: CoinGeckoIdResolver,
) : OkHttpTextWebSocketSession(
    webSocketFactory = webSocketFactory,
    request = Request.Builder()
        .url("wss://cables.coingecko.com/cable")
        .header("Origin", "https://www.coingecko.com")
        .build(),
    dispatcher = dispatcher
),
    CoinGeckoWebSocketSession {
    internal val welcomeMessage = CompletableDeferred<CableMessage>()
    private val logger = getLogger<CoinGeckoWebSocketSessionImpl>()
    internal val subscribedCoin = HashSet<CoinId>()
    internal val messageListeners = HashSet<CableMessageListener>()
    internal val pendingSubscribeRequest = Channel<Set<CoinId>>(Channel.CONFLATED)
    private val messageAdapter = moshi.adapter(CableMessage::class.java)
    private val commandAdapter = moshi.adapter(CableCommand::class.java)
    internal var operationTimeoutOverride: Long? = null
    internal var operationTimeoutFactorOverride: Double? = null
    internal var operationInitialDelayOverride: Long? = null

    internal val subscribeCoinsJobs = scope.launch {
        logger.debug("process coins job")
        for (coins in pendingSubscribeRequest) {
            logger.debug("process coins: $coins")
            subscribeInternal(coins = coins)
        }
    }

    internal val processMessageJob = scope.launch {
        logger.debug("process message job")
        val toRemove = mutableListOf<CableMessageListener>()
        for (message in incoming) {
            logger.debug("process message: $message")
            val cableMessage = runCatching {
                decodeMessage(message)
            }.onFailure {
                logger.error(Exception("Decode message failed", it))
            }.getOrNull() ?: continue
            messageListeners.forEach { listener ->
                if (listener.isActive) {
                    listener.invoke(cableMessage)
                }
                // listener state might changed after invoke
                if (!listener.isActive) {
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

    private fun createWelcomeMessageListener(): CableMessageListener {
        return object : CableMessageListener() {
            val timeoutJob = scope.launch {
                delay(operationTimeoutOverride ?: 10000)
                if (isActive) {
                    isActive = false
                    welcomeMessage.completeExceptionally(
                        Exception("Unable to receive welcome message")
                    )
                }
            }

            override fun invoke(message: CableMessage) {
                super.invoke(message)
                if (message == WELCOME_MESSAGE) {
                    welcomeMessage.complete(message)
                    isActive = false
                    timeoutJob.cancel()
                }
            }
        }
    }

    private fun decodeMessage(json: String): CableMessage {
        return messageAdapter.fromJson(json).notNull {
            "Unknown message: $json"
        }
    }

    internal suspend fun sendCommand(command: CableCommand) {
        try {
            welcomeMessage.await()
        } catch (exception: Exception) {
            // TODO log to system
            return
        }
        val json: String = commandAdapter.toJson(command)
        outgoing.trySend(json)
    }

    override fun subscribe(coins: Set<CoinId>): ChannelResult<Unit> {
        logger.debug("subscribe $coins")
        return pendingSubscribeRequest.trySend(coins)
    }

    internal suspend fun subscribeInternal(coins: Set<CoinId>) {
        val toSubscribe = coins.filter { it !in subscribedCoin }
        val toUnsubscribe = subscribedCoin.filter { it !in coins }
        coroutineScope {
            toSubscribe.forEach {
                operation("subscribe $it") {
                    subscribe(it)
                }
            }
            toUnsubscribe.forEach {
                operation("unsubscribe $it") {
                    unsubscribe(it)
                }
            }
        }
        subscribedCoin.clear()
        subscribedCoin.addAll(coins)
        logger.debug("subscribe $coins done")
    }

    internal suspend fun subscribe(coin: CoinId): Boolean {
        logger.debug("subscribe coin: $coin")
        if (coin == BITCOIN_ID) {
            return false
        }
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
        sendAndReceive(command, expectedMessage)
        return true
    }

    private suspend fun sendAndReceive(
        command: CableCommand,
        expectedMessage: CableMessage,
    ) {
        suspendCancellableCoroutine<Unit> { cont ->
            val listener = object : CableMessageListener() {
                override fun invoke(message: CableMessage) {
                    if (message == expectedMessage) {
                        cont.resume(Unit)
                        isActive = false
                    }
                }
            }
            cont.invokeOnCancellation {
                listener.isActive = false
            }
            scope.launch {
                sendCommand(command)
                messageListeners.add(listener)
            }
        }
    }

    internal suspend fun unsubscribe(coin: CoinId): Boolean {
        logger.debug("unsubscribe $coin")
        if (coin == BITCOIN_ID) {
            return false
        }
        val coinId = coinGeckoIdResolver.resolve(coin.id)
        val command = CableCommand(
            command = "unsubscribe",
            identifier = CableIdentifier(
                channel = "PChannel",
                coinId = coinId
            )
        )
        sendCommand(command)
        return true
    }

    override fun start() {
        super.start()
        subscribeCEChannel()
        addWelcomeMessageListener()
    }

    override fun closeSelf(code: Int, reason: String) {
        super.closeSelf(code, reason)
        pendingSubscribeRequest.close()
        subscribeCoinsJobs.cancel()
        processMessageJob.cancel()
    }

    internal fun addWelcomeMessageListener() {
        scope.launch {
            messageListeners.add(createWelcomeMessageListener())
        }
    }

    internal fun subscribeCEChannel() {
        val command = CableCommand("subscribe", CableIdentifier("CEChannel"))
        scope.operation("subscribe CEChannel") {
            sendAndReceive(command, CONFIRM_CE_MESSAGE)
        }
    }

    private fun CoroutineScope.operation(
        name: String,
        retry: Int = 3,
        timeout: Long = 10000,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> Unit,
    ) {
        val operationTimeout = operationTimeoutOverride ?: timeout
        val operationTimeoutFactor = operationTimeoutFactorOverride ?: factor
        val operationInitialDelay = operationInitialDelayOverride ?: initialDelay
        launch {
            var attempt = 0
            var error: Exception? = null
            var currentDelay = operationInitialDelay
            while (attempt < retry) {
                try {
                    if (attempt > 0) {
                        logger.debug(
                            "retry $name attempt #${attempt + 1} " +
                                "delay $currentDelay because ${error?.message}"
                        )
                    }
                    return@launch withTimeout(operationTimeout) {
                        block()
                    }
                } catch (ex: Exception) {
                    if (ex is CancellationException && ex !is TimeoutCancellationException) {
                        throw ex
                    }
                    error = ex
                    delay(currentDelay)
                    currentDelay = (currentDelay * operationTimeoutFactor).toLong()
                        .coerceAtMost(maxDelay)
                } finally {
                    attempt++
                }
            }
            logger.error(
                error?.message ?: "Unknown error",
                Exception("Unable to $name", error)
            )
            closeSelf(
                code = CloseReason.Codes.VIOLATED_POLICY.code,
                reason = "Unable to $name"
            )
        }
    }

    companion object {
        private val CONFIRM_CE_MESSAGE = CableMessage(
            type = "confirm_subscription",
            identifier = CableIdentifier(channel = "CEChannel")
        )

        private val WELCOME_MESSAGE = CableMessage(
            type = "welcome"
        )

        private val BITCOIN_ID = CoinId("bitcoin", "coingecko")
    }

    @AssistedFactory
    interface Factory : CoinGeckoWebSocketSession.Factory {
        override fun create(
            webSocketFactory: WebSocket.Factory,
            dispatcher: CoroutineDispatcher,
            cableMessages: SendChannel<CableMessage>,
        ): CoinGeckoWebSocketSessionImpl
    }
}
