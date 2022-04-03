package com.rainyseason.coc.backend.price.alert

import com.rainyseason.coc.backend.data.model.CoinId

data class PriceAlert(
    val coinId: CoinId,
    val currency: String,
)
