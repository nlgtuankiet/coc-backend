package com.rainyseason.coc.backend.price.alert

import com.rainyseason.coc.backend.util.getLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceAlertController @Inject constructor() {
    private val logger = getLogger<PriceAlertController>()
    fun start() {
        logger.debug("start")
    }
}
