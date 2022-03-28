package com.rainyseason.coc.backend.data.coingecko

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CoinGeckoIdResolverImplTest {
    @Test
    fun `extract id from image link`() {
        val provider = object : CoinGeckoLargeImageProvider {
            override suspend fun getLargeImage(id: String): String {
                return when (id) {
                    "bitcoin" ->
                        "https://assets.coingecko.com" +
                            "/coins/images/1/large/bitcoin.png?1547033579"
                    "ethereum" ->
                        "https://assets.coingecko.com" +
                            "/coins/images/279/large/ethereum.png?1595348880"
                    else -> error("Not support")
                }
            }
        }
        val impl = CoinGeckoIdResolverImpl(provider)
        runBlocking {
            assertEquals(1, impl.resolve("bitcoin"))
            assertEquals(279, impl.resolve("ethereum"))
        }
    }

    @Test
    fun `network use`() {
        val networkHit = AtomicInteger()
        val provider = object : CoinGeckoLargeImageProvider {
            override suspend fun getLargeImage(id: String): String {
                networkHit.incrementAndGet()
                return when (id) {
                    "bitcoin" ->
                        "https://assets.coingecko.com" +
                            "/coins/images/1/large/bitcoin.png?1547033579"
                    "ethereum" ->
                        "https://assets.coingecko.com" +
                            "/coins/images/279/large/ethereum.png?1595348880"
                    else -> error("Not support")
                }
            }
        }
        val impl = CoinGeckoIdResolverImpl(provider)
        runBlocking(Dispatchers.IO) {
            repeat(1000) {
                launch {
                    val id = impl.resolve("bitcoin")
                    assertEquals(1, id)
                }
            }
            repeat(1000) {
                launch {
                    val id = impl.resolve("ethereum")
                    assertEquals(279, id)
                }
            }
        }
        assertEquals(2, networkHit.get())
    }

    @Test
    fun `cache use`() {
        val provider = object : CoinGeckoLargeImageProvider {
            override suspend fun getLargeImage(id: String): String {
                error("Not support")
            }
        }
        val impl = CoinGeckoIdResolverImpl(provider)

        runBlocking(Dispatchers.IO) {
            impl.populateCache(
                mapOf(
                    "bitcoin" to 1,
                    "ethereum" to 2,
                )
            )
            assertEquals(1, impl.resolve("bitcoin"))
            assertEquals(279, impl.resolve("ethereum"))
        }
    }

    @Test
    fun `independent mutex`() {
        val networkHit = AtomicInteger()
        val provider = object : CoinGeckoLargeImageProvider {
            override suspend fun getLargeImage(id: String): String {
                networkHit.incrementAndGet()
                delay(1000)
                return when (id) {
                    "bitcoin" ->
                        "https://assets.coingecko.com" +
                            "/coins/images/1/large/bitcoin.png?1547033579"
                    "ethereum" ->
                        "https://assets.coingecko.com" +
                            "/coins/images/279/large/ethereum.png?1595348880"
                    else -> error("Not support")
                }
            }
        }
        val impl = CoinGeckoIdResolverImpl(provider)
        val time = measureTimeMillis {
            runBlocking(Dispatchers.IO) {
                launch {
                    val id = impl.resolve("bitcoin")
                    assertEquals(1, id)
                }
                launch {
                    val id = impl.resolve("ethereum")
                    assertEquals(279, id)
                }
            }
        }

        assertTrue(time in 1000..1999, "Time not satisfy: $time")
    }
}
