package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.awaitState
import com.rainyseason.coc.backend.data.coingecko.model.CableCommand
import com.rainyseason.coc.backend.data.coingecko.model.CableIdentifier
import com.rainyseason.coc.backend.data.coingecko.model.CableMessage
import com.rainyseason.coc.backend.data.model.CoinId
import com.rainyseason.coc.backend.data.ws.CloseReason
import com.rainyseason.coc.backend.di.AppModule
import com.rainyseason.coc.backend.take
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CoinGeckoWebSocketSessionImpl]
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CoinGeckoWebSocketSessionImplTest {
    internal object InMemoryIdResolver : CoinGeckoIdResolver {
        override suspend fun resolve(id: String): Int {
            return when (id) {
                "bitcoin" -> 1
                "ethereum" -> 276
                "doge" -> 420
                "doge1" -> 421
                "doge2" -> 422
                else -> error("Not support $id")
            }
        }
    }

    data class TestObjects(
        val session: CoinGeckoWebSocketSessionImpl,
        val webSocket: WebSocket,
        val outgoing: Channel<String>,
    )

    private fun createTestObjects(
        coinGeckoIdResolver: CoinGeckoIdResolver? = null,
        outMessages: Channel<CableMessage> = Channel(Channel.UNLIMITED),
    ): TestObjects {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://google.com").build()
        val webSocket = client.newWebSocket(request, object : WebSocketListener() {})
        val session = CoinGeckoWebSocketSessionImpl(
            moshi = AppModule.moshi(),
            coinGeckoIdResolver = coinGeckoIdResolver ?: InMemoryIdResolver,
            webSocketFactory = client,
            dispatcher = Dispatchers.IO,
            outMessages = outMessages
        )
        val outgoing = Channel<String>(Channel.UNLIMITED)
        session.outgoingOverride = outgoing
        return TestObjects(
            session,
            webSocket,
            outgoing
        )
    }

    @Test
    fun `subscribe one coin correct`() {
        runBlocking {
            val (session, webSocket, outgoing) = createTestObjects()
            session.moveToAfterWelcomeState()
            session.subscribe(
                setOf(
                    CoinId("ethereum", "coingecko")
                )
            )

            session.awaitState {
                messageListeners.size == 1
            }

            // assert outgoing
            assertEquals(
                listOf(
                    """{"command":"subscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"276\"}"}"""
                ),
                outgoing.take(1)
            )

            session.onMessage(
                webSocket,
                """{"identifier":"{\"channel\":\"PChannel\",\"m\":\"276\"}","type":"confirm_subscription"}"""
            )

            session.awaitState {
                subscribedCoin.size == 1
            }
            assertEquals(setOf(CoinId("ethereum", "coingecko")), session.subscribedCoin)
            assertTrue(session.messageListeners.isEmpty())
        }
    }

    @Test
    fun `subscribe with throttle`() {
        runBlocking {
            val (session, webSocket, outgoing) = createTestObjects()
            session.moveToAfterWelcomeState()
            session.subscribe(
                setOf(
                    CoinId("ethereum", "coingecko")
                )
            )

            session.awaitState {
                messageListeners.size == 1
            }

            // assert outgoing
            assertEquals(
                listOf(
                    """{"command":"subscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"276\"}"}"""
                ),
                outgoing.take(1)
            )

            // at this point first subscribe job is still not finish
            // enqueue more subscribe request to test throttle effect
            session.subscribe(
                setOf(CoinId("doge", "coingecko"))
            )
            session.subscribe(
                setOf(
                    CoinId("doge", "coingecko"),
                    CoinId("doge1", "coingecko")
                )
            )
            session.subscribe(
                setOf(
                    CoinId("doge", "coingecko"),
                    CoinId("doge1", "coingecko"),
                    CoinId("doge1", "coingecko"),
                    CoinId("doge2", "coingecko"),
                )
            )

            session.onMessage(
                webSocket,
                """{"identifier":"{\"channel\":\"PChannel\",\"m\":\"276\"}","type":"confirm_subscription"}"""
            )

            session.awaitState {
                subscribedCoin.size == 1
            }
            assertEquals(setOf(CoinId("ethereum", "coingecko")), session.subscribedCoin)

            // assert outgoing
            assertEquals(
                setOf(
                    """{"command":"subscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"420\"}"}""",
                    """{"command":"subscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"421\"}"}""",
                    """{"command":"subscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"422\"}"}""",
                    """{"command":"unsubscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"276\"}"}""",
                ),
                outgoing.take(4).toSet()
            )

            session.onMessage(
                webSocket,
                """{"identifier":"{\"channel\":\"PChannel\",\"m\":\"420\"}","type":"confirm_subscription"}"""
            )
            session.onMessage(
                webSocket,
                """{"identifier":"{\"channel\":\"PChannel\",\"m\":\"421\"}","type":"confirm_subscription"}"""
            )
            session.onMessage(
                webSocket,
                """{"identifier":"{\"channel\":\"PChannel\",\"m\":\"422\"}","type":"confirm_subscription"}"""
            )

            session.awaitState {
                subscribedCoin.size == 3
            }
            assertEquals(
                setOf(
                    CoinId("doge", "coingecko"),
                    CoinId("doge1", "coingecko"),
                    CoinId("doge2", "coingecko"),
                ),
                session.subscribedCoin
            )
            assertTrue(outgoing.isEmpty)
        }
    }

    @Test
    fun `subscribe to CE channel success`() {
        runBlocking {
            val (session, webSocket, outgoing) = createTestObjects()
            session.moveToAfterWelcomeState()
            session.subscribeCEChannel()

            session.awaitState {
                messageListeners.size == 1
            }
            assertEquals(
                listOf("""{"command":"subscribe","identifier":"{\"channel\":\"CEChannel\"}"}"""),
                outgoing.take(1)
            )
            session.onMessage(
                webSocket,
                """{"identifier":"{\"channel\":\"CEChannel\"}","type":"confirm_subscription"}"""
            )
            session.awaitState {
                messageListeners.size == 0
            }
            assertTrue(outgoing.isEmpty)
        }
    }

    @Test
    fun `subscribe to CE channel fail`() {
        runBlocking {
            val (session, _, _) = createTestObjects()
            session.apply {
                operationTimeoutOverride = 1
                operationTimeoutFactorOverride = 1.0
                operationInitialDelayOverride = 1
            }
            session.subscribeCEChannel()

            assertEquals(
                CloseReason(
                    CloseReason.Codes.VIOLATED_POLICY,
                    "Unable to subscribe CEChannel"
                ),
                session.closeReason.await()
            )
        }
    }

    @Test
    fun `subscribe coin fail because unable to resolve id`() {
        runBlocking {
            val (session, _, _) = createTestObjects(
                coinGeckoIdResolver = object : CoinGeckoIdResolver {
                    override suspend fun resolve(id: String): Int {
                        throw IOException("No network")
                    }
                }
            )

            session.apply {
                operationTimeoutOverride = 100
                operationTimeoutFactorOverride = 1.1
                operationInitialDelayOverride = 100
            }

            session.subscribe(
                setOf(
                    CoinId("ethereum", "coingecko")
                )
            )

            assertEquals(
                CloseReason(
                    CloseReason.Codes.VIOLATED_POLICY,
                    "Unable to subscribe ${CoinId("ethereum", "coingecko")}"
                ),
                session.closeReason.await()
            )

            assertTrue(session.messageListeners.isEmpty())
        }
    }

    @Test
    fun `interact with session after close has no effect`() {
        val (session, _, _) = createTestObjects()
        session.closeSelf(1, "test")

        assertTrue(session.pendingSubscribeRequest.isClosedForSend)
        runBlocking {
            assertEquals(
                CloseReason(
                    1,
                    "test"
                ),
                session.closeReason.await()
            )
        }
        val result = session.subscribe(emptySet())
        assertTrue(result.isClosed)
    }

    @Test
    fun `closeSelf close all resource`() {
        val (session, _, _) = createTestObjects()
        session.closeSelf(1, "")
        assertTrue(session.pendingSubscribeRequest.isClosedForSend)
        assertTrue(session.pendingSubscribeRequest.isClosedForReceive)
        assertTrue(session.subscribeCoinsJobs.run { isCancelled || isCompleted })
        assertTrue(session.processMessageJob.run { isCancelled || isCompleted })
    }

    @Test
    fun `subscribe coin real world flow`() {
        runBlocking {
            val (session, webSocket, outgoing) = createTestObjects()
            session.moveToAfterWelcomeState()
            session.subscribeCEChannel()
            session.awaitState {
                messageListeners.size == 1
            }
            session.subscribe(
                setOf(
                    CoinId("ethereum", "coingecko")
                )
            )
            session.awaitState {
                messageListeners.size == 2
            }
            // assert outgoing
            assertEquals(
                listOf(
                    """{"command":"subscribe","identifier":"{\"channel\":\"CEChannel\"}"}""",
                    """{"command":"subscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"276\"}"}"""
                ).toSet(),
                outgoing.take(2).toSet()
            )
            session.onMessage(
                webSocket,
                """{"identifier":"{\"channel\":\"CEChannel\"}","type":"confirm_subscription"}"""
            )
            session.onMessage(
                webSocket,
                """{"identifier":"{\"channel\":\"PChannel\",\"m\":\"276\"}","type":"confirm_subscription"}"""
            )

            session.awaitState {
                subscribedCoin.size == 1
            }
            assertEquals(setOf(CoinId("ethereum", "coingecko")), session.subscribedCoin)
            assertTrue(session.messageListeners.isEmpty())
            assertTrue(outgoing.isEmpty)
        }
    }

    @Test
    fun `wait for welcome success first, then send command`() {
        runBlocking {
            val (session, webSocket, outgoing) = createTestObjects()
            session.onMessage(webSocket, """{"type":"welcome"}""")
            val command = CableCommand(
                command = "test_command",
                identifier = CableIdentifier(channel = "test")
            )
            val cableMessage = CableMessage(type = "welcome")
            session.welcomeMessage.complete(cableMessage)
            session.sendCommand(command) // should return immediately
            assertEquals(
                listOf("""{"command":"test_command","identifier":"{\"channel\":\"test\"}"}"""),
                outgoing.take(1).toList()
            )
            assertTrue(outgoing.isEmpty)
        }
    }

    @Test
    fun `send command should wait for welcome message first`() {
        runBlocking {
            val (session, webSocket, outgoing) = createTestObjects()
            session.onMessage(webSocket, """{"type":"welcome"}""")
            val command = CableCommand(
                command = "test_command",
                identifier = CableIdentifier(channel = "test")
            )
            // to fail this test case, try to comment out the wait for welcome message
            launch { session.sendCommand(command) }
            yield()
            assertTrue(outgoing.isEmpty)
            val cableMessage = CableMessage(type = "welcome")
            session.welcomeMessage.complete(cableMessage)

            assertEquals(
                listOf("""{"command":"test_command","identifier":"{\"channel\":\"test\"}"}"""),
                outgoing.take(1).toList()
            )
            assertTrue(outgoing.isEmpty)
        }
    }

    @Test
    fun `send command do nothing on failed to receive welcome message`() {
        runBlocking {
            val (session, _, outgoing) = createTestObjects()
            session.welcomeMessage.completeExceptionally(Exception())
            val command = CableCommand(
                command = "test_command",
                identifier = CableIdentifier(channel = "test")
            )
            session.sendCommand(command)
            yield()
            assertTrue(outgoing.isEmpty)
        }
    }

    @Test
    fun `skip message when decode message failed`() {
        val (session: CoinGeckoWebSocketSession, webSocket, _) = createTestObjects()
        var receiveMessage: CableMessage? = null

        session.messageListeners.add(
            object : CableMessageListener() {
                override fun invoke(message: CableMessage) {
                    super.invoke(message)
                    receiveMessage = message
                    isActive = false
                }
            }
        )
        session.onMessage(webSocket, "invalid message")
        session.onMessage(webSocket, """{"type":"a"}""")
        runBlocking {
            session.awaitState {
                messageListeners.size == 0
            }
        }
        assertEquals(CableMessage(type = "a"), receiveMessage)
    }

    @Test
    fun `subscribe and unsubscribe to bitcoin does nothing`() {
        val (session: CoinGeckoWebSocketSession, _, _) = createTestObjects()
        runBlocking {
            val subscribeResult = withTimeoutOrNull(300) {
                session.subscribe(CoinId("bitcoin", "coingecko"))
            }
            assertEquals(false, subscribeResult)

            val unSubscribeResult = withTimeoutOrNull(300) {
                session.unsubscribe(CoinId("bitcoin", "coingecko"))
            }
            assertEquals(false, unSubscribeResult)
        }
    }

    private fun CoinGeckoWebSocketSessionImpl.moveToAfterWelcomeState() {
        welcomeMessage.complete(CableMessage(type = "welcome"))
    }
}
