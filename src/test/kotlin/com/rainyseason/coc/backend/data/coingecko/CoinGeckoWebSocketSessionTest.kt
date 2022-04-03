package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.data.RawJsonAdapter
import com.rainyseason.coc.backend.data.model.CoinId
import com.rainyseason.coc.backend.price.alert.PriceAlert
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CoinGeckoWebSocketSession]
 */
internal class CoinGeckoWebSocketSessionTest {
    private object InMemoryIdResolver : CoinGeckoIdResolver {
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
        val session: CoinGeckoWebSocketSession,
        val webSocket: WebSocket,
        val outgoing: Channel<String>,
    )

    private fun createTestObjects(): TestObjects {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://google.com").build()
        val webSocket = client.newWebSocket(request, object : WebSocketListener() {})
        val session = CoinGeckoWebSocketSession(
            moshi = Moshi.Builder().add(RawJsonAdapter).build(),
            coinGeckoIdResolver = InMemoryIdResolver,
            webSocketFactory = client,
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
        runBlocking(Dispatchers.Default) {
            val (session, webSocket, outgoing) = createTestObjects()

            session.subscribe(
                listOf(
                    PriceAlert(CoinId("ethereum", "coingecko"), "usd")
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

            session.subscribe(
                listOf(
                    PriceAlert(CoinId("ethereum", "coingecko"), "usd")
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
                listOf(
                    PriceAlert(CoinId("doge", "coingecko"), "usd"),
                )
            )
            session.subscribe(
                listOf(
                    PriceAlert(CoinId("doge", "coingecko"), "usd"),
                    PriceAlert(CoinId("doge1", "coingecko"), "usd"),
                )
            )
            session.subscribe(
                listOf(
                    PriceAlert(CoinId("doge", "coingecko"), "usd"),
                    PriceAlert(CoinId("doge1", "coingecko"), "usd"),
                    PriceAlert(CoinId("doge1", "coingecko"), "vnd"),
                    PriceAlert(CoinId("doge2", "coingecko"), "vnd"),
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

    private suspend fun CoinGeckoWebSocketSession.awaitState(
        block: CoinGeckoWebSocketSession.() -> Boolean,
    ) {
        withTimeout(5000) {
            while (!block.invoke(this@awaitState)) {
                yield()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun <T> Channel<T>.take(limit: Int): List<T> {
        val result = mutableListOf<T>()
        while (result.size != limit) {
            result.add(this.receive())
        }
        return result
    }
}
