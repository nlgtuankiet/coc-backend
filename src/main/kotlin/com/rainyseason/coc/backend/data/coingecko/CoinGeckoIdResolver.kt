package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.util.StringMutex
import com.rainyseason.coc.backend.util.notNull
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface CoinGeckoIdResolver {
    suspend fun resolve(id: String): Int
}

@Singleton
class CoinGeckoIdResolverImpl @Inject constructor(
    private val imageProvider: CoinGeckoLargeImageProvider,
) : CoinGeckoIdResolver {
    private val cache = HashMap<String, Int>()
    private val cacheMutex = StringMutex()
    private val idRegex = """images/(\d+)/""".toRegex()

    override suspend fun resolve(id: String): Int {
        return cacheMutex.withLock(id) {
            val old = cache[id]
            if (old != null) {
                return@withLock old
            }
            val new = resolveFromNetwork(id)
            cache[id] = new
            new
        }
    }

    suspend fun populateCache(data: Map<String, Int>) {
        data.forEach { (key, value) ->
            cacheMutex.withLock(key) {
                cache[key] = value
            }
        }
    }

    private suspend fun resolveFromNetwork(id: String): Int {
        val image = imageProvider.getLargeImage(id)
        return getIntId(image)
    }

    private fun getIntId(image: String): Int {
        return idRegex.find(image)
            .notNull { "Not found" }
            .groupValues[1].toInt()
            .also { require(it > 0) }
    }
}
