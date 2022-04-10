package com.rainyseason.coc.backend.data.coingecko

import com.rainyseason.coc.backend.data.coingecko.model.CableMessage

abstract class CableMessageListener : Function1<CableMessage, Unit> {
    @Volatile
    internal var isActive = true

    override fun invoke(message: CableMessage) {
    }
}
