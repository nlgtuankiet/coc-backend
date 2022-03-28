package com.rainyseason.coc.backend.util

import kotlinx.coroutines.sync.Mutex
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class StringMutex {
    val mutexSet = HashMap<String, Mutex>()

    @OptIn(ExperimentalContracts::class)
    suspend inline fun <T> withLock(owner: String, action: () -> T): T {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }
        val intern = owner.intern()
        val mutex = synchronized(mutexSet) {
            val oldMutex = mutexSet[intern]
            if (oldMutex != null) {
                return@synchronized oldMutex
            }
            val mutex = Mutex()
            mutexSet[intern] = mutex
            mutex
        }

        mutex.lock()
        try {
            return action()
        } finally {
            mutex.unlock()
        }
    }
}
