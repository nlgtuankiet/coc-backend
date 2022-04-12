package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.data.coingecko.model.CableMessage
import com.rainyseason.coc.backend.data.model.CoinId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class CoinGeckoPriceMessageProvider @Inject constructor(
    private val sessionFactory: CoinGeckoWebSocketSession.Factory,
    private val webSocketFactory: WebSocket.Factory,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    internal val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    private val cableMessages = Channel<CableMessage>(Channel.UNLIMITED)
    internal val cableMessageListeners = HashMap<CableMessageListener, CoinId>()
    private val startNewSessionRequest = Channel<Unit>(Channel.CONFLATED)
    internal var createSession = Job()
    internal lateinit var currentSession: CoinGeckoWebSocketSession
    private val messageTypeBlacklist = hashSetOf("welcome", "confirm_subscription", "ping")

    fun start() {
        scope.launch {
            for (message in cableMessages) {
                processMessages(message)
            }
        }
        scope.launch {
            for (request in startNewSessionRequest) {
                startSession()
            }
        }
        startNewSessionRequest.trySend(Unit)
    }

    suspend fun processMessages(message: CableMessage) {
        if (message.type in messageTypeBlacklist) {
            return
        }
        val toRemove = mutableListOf<CableMessageListener>()
        cableMessageListeners.forEach { (listener, _) ->
            if (listener.isActive) {
                listener.invoke(message)
            }
            // listener state might changed after invoke
            if (!listener.isActive) {
                toRemove.add(listener)
            }
        }
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { listener ->
                cableMessageListeners.remove(listener)
            }
            syncSessionState()
        }
    }

    internal suspend fun startSession() {
        currentSession = sessionFactory.create(
            webSocketFactory = webSocketFactory,
            dispatcher = dispatcher,
            cableMessages = cableMessages,
        )
        currentSession.start()
        createSession.complete()
        currentSession.closeReason.invokeOnCompletion {
            // TODO log error
            startNewSessionRequest.trySend(Unit)
        }
        syncSessionState()
    }

    fun subscribe(
        coinId: CoinId,
        cableMessageListener: CableMessageListener,
    ) {
        scope.launch {
            cableMessageListeners[cableMessageListener] = coinId
            syncSessionState()
        }
    }

    private suspend fun syncSessionState() {
        createSession.join()
        val coins = HashSet(cableMessageListeners.values)
        currentSession.subscribe(coins)
    }
}
