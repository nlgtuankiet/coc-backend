package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.awaitState
import com.rainyseason.coc.backend.data.coingecko.model.CableIdentifier
import com.rainyseason.coc.backend.data.coingecko.model.CableMessage
import com.rainyseason.coc.backend.data.coingecko.model.LongMessage
import com.rainyseason.coc.backend.data.coingecko.model.PriceMessage
import com.rainyseason.coc.backend.data.model.CoinId
import com.rainyseason.coc.backend.data.ws.CloseReason
import com.rainyseason.coc.backend.take
import com.rainyseason.coc.backend.util.notNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

// TODO need more test related to listener getting remove and inactive
internal class CoinGeckoPriceMessageProviderTest {
    private data class TestObjects(
        val provider: CoinGeckoPriceMessageProvider,
    )

    private fun createTestObjects(
        sessionFactory: () -> CoinGeckoWebSocketSession,
    ): TestObjects {
        val factory = object : CoinGeckoWebSocketSession.Factory {
            override fun create(
                webSocketFactory: WebSocket.Factory,
                dispatcher: CoroutineDispatcher,
                cableMessages: SendChannel<CableMessage>,
            ): CoinGeckoWebSocketSession {
                return sessionFactory.invoke()
            }
        }
        val provider = CoinGeckoPriceMessageProvider(
            sessionFactory = factory,
            webSocketFactory = OkHttpClient()
        )
        return TestObjects(
            provider = provider
        )
    }

    private fun createNoopListener(): CableMessageListener {
        return object : CableMessageListener() {
        }
    }

    @Test
    fun `start result in sync session request`() {
        val subscribeCalled = Job()
        val coinsList = listOf(
            CoinId("bitcoin", "coingecko"),
            CoinId("bitcoin", "coingecko"),
            CoinId("dogecoin", "coingecko"),
        )
        val (provider) = createTestObjects(
            sessionFactory = {
                object : CoinGeckoWebSocketSession {
                    override fun start() {
                    }

                    override val closeReason: Deferred<CloseReason>
                        get() = CompletableDeferred()

                    override fun subscribe(coins: Set<CoinId>): ChannelResult<Unit> {
                        assertEquals(coinsList.toSet(), coins.toSet())
                        subscribeCalled.complete()
                        return Channel<Unit>().trySend(Unit)
                    }
                }
            }
        )
        provider.cableMessageListeners.let { listeners ->
            coinsList.forEach { listeners[createNoopListener()] = it }
        }
        provider.start()
        runBlocking {
            withTimeoutOrNull(500) {
                subscribeCalled.join()
            }.notNull { "failed" }
        }
    }

    @Test
    fun `start session result in correct calls and state`() {
        val subscribeCalled = Job()
        val coinsList = listOf(
            CoinId("bitcoin", "coingecko"),
            CoinId("bitcoin", "coingecko"),
            CoinId("dogecoin", "coingecko"),
        )
        val startCalled = Job()
        val session = object : CoinGeckoWebSocketSession {
            override fun start() {
                startCalled.complete()
            }

            override val closeReason: Deferred<CloseReason>
                get() = CompletableDeferred()

            override fun subscribe(coins: Set<CoinId>): ChannelResult<Unit> {
                assertEquals(coinsList.toSet(), coins.toSet())
                subscribeCalled.complete()
                return Channel<Unit>().trySend(Unit)
            }
        }
        val (provider) = createTestObjects(
            sessionFactory = {
                session
            }
        )
        provider.cableMessageListeners.let { listeners ->
            coinsList.forEach { listeners[createNoopListener()] = it }
        }
        provider.scope.launch {
            provider.startSession()
        }
        runBlocking {
            withTimeoutOrNull(500) {
                subscribeCalled.join()
                startCalled.join()
                provider.createSession.join()
                assertEquals(session, provider.currentSession)
            }.notNull { "failed" }
        }
    }

    @Test
    fun `auto start new session when current session failed`() {
        val session1CloseReason = CompletableDeferred<CloseReason>()
        val session1SubscribeCalled = Job()
        val session1 = object : CoinGeckoWebSocketSession {
            override fun start() {
            }

            override val closeReason: Deferred<CloseReason>
                get() = session1CloseReason

            override fun subscribe(coins: Set<CoinId>): ChannelResult<Unit> {
                session1SubscribeCalled.complete()
                return Channel<Unit>().trySend(Unit)
            }
        }
        val session2SubscribeCalled = Job()
        val session2 = object : CoinGeckoWebSocketSession {
            override fun start() {
            }

            override val closeReason: Deferred<CloseReason>
                get() = CompletableDeferred()

            override fun subscribe(coins: Set<CoinId>): ChannelResult<Unit> {
                session2SubscribeCalled.complete()
                return Channel<Unit>().trySend(Unit)
            }
        }
        var count = 0
        val sessions = listOf(session1, session2)
        val (provider) = createTestObjects(
            sessionFactory = {
                sessions[count++]
            }
        )
        provider.start()
        runBlocking {
            withTimeoutOrNull(1000) {
                provider.createSession.join()
                assertEquals(session1, provider.currentSession)
                session1SubscribeCalled.join()
                assertEquals(session1, provider.currentSession)
                session1CloseReason.completeExceptionally(Exception())
                session2SubscribeCalled.join()
                assertEquals(session2, provider.currentSession)
            }.notNull { "failed" }
        }
    }

    @Test
    fun `subscribe result in sync session call`() {
        val subscribeCalled = Job()
        val bitcoinCoinId = CoinId("bitcoin", "coingecko")
        val session = object : CoinGeckoWebSocketSession {
            override fun start() {
            }

            override val closeReason: Deferred<CloseReason>
                get() = CompletableDeferred()

            override fun subscribe(coins: Set<CoinId>): ChannelResult<Unit> {
                assertEquals(setOf(bitcoinCoinId), coins.toSet())
                subscribeCalled.complete()
                return Channel<Unit>().trySend(Unit)
            }
        }
        val (provider) = createTestObjects(
            sessionFactory = {
                session
            }
        )
        provider.currentSession = session
        provider.createSession.complete()
        val listener = createNoopListener()
        provider.subscribe(CoinId("bitcoin", "coingecko"), listener)
        runBlocking {
            withTimeoutOrNull(500) {
                subscribeCalled.join()
                assertEquals(mapOf(listener to bitcoinCoinId), provider.cableMessageListeners)
            }.notNull { "failed" }
        }
    }

    @Test
    fun `should notify listener with filtered`() {
        val session = object : CoinGeckoWebSocketSession {
            override fun start() {
            }

            override val closeReason: Deferred<CloseReason>
                get() = CompletableDeferred()

            override fun subscribe(coins: Set<CoinId>): ChannelResult<Unit> {
                return Channel<Unit>().trySend(Unit)
            }
        }
        val (provider) = createTestObjects(
            sessionFactory = {
                session
            }
        )
        val bitcoin = CoinId("dogecoin", "coingecko")
        val receivedMessage = Channel<CableMessage>(Channel.UNLIMITED)
        val listener = object : CableMessageListener() {
            override fun invoke(message: CableMessage) {
                receivedMessage.trySend(message)
            }
        }
        provider.start()
        provider.subscribe(bitcoin, listener)
        runBlocking {
            withTimeoutOrNull(500) {
                provider.awaitState {
                    cableMessageListeners.size == 1
                }
                val messages = listOf(
                    CableMessage(type = "welcome"),
                    CableMessage(
                        type = "confirm_subscription",
                        identifier = CableIdentifier(channel = "CEChannel")
                    ),
                    CableMessage(
                        type = "confirm_subscription",
                        identifier = CableIdentifier(channel = "PChannel", coinId = 1),
                    ),
                    CableMessage(
                        type = "ping",
                        message = LongMessage(value = 1649772490)
                    ),
                    CableMessage(
                        identifier = CableIdentifier(channel = "PChannel", coinId = 12407),
                        message = PriceMessage(
                            coinId = 12407,
                            percent = 0.123
                        )
                    ),
                    CableMessage(
                        identifier = CableIdentifier(channel = "PChannel", coinId = 12407),
                        message = PriceMessage(
                            coinId = 321,
                            bitcoinPrice = mapOf("aed" to 148038.248)
                        )
                    ),
                )
                provider.scope.launch {
                    messages.forEach { message ->
                        provider.processMessages(message)
                    }
                }
                assertEquals(messages.takeLast(2), receivedMessage.take(2))
            }.notNull { "failed" }
        }
    }
}
