package com.rainyseason.coc.backend.data.coingecko.model

import com.rainyseason.coc.backend.data.RawJsonAdapter
import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CableMessageTest {
    private val moshi = Moshi.Builder()
        .add(RawJsonAdapter)
        .build()

    @Test
    fun `encode decode welcome`() {
        val json = """{"type":"welcome"}"""
        val expected = CableMessage(
            type = "welcome"
        )
        val adapter = moshi.adapter(CableMessage::class.java)
        assertEquals(expected, adapter.fromJson(json))
        assertEquals(json, adapter.toJson(expected))
    }

    @Test
    fun `encode decode confirm subscription ce channel`() {
        val json = """{"identifier":"{\"channel\":\"CEChannel\"}","type":"confirm_subscription"}"""
        val model = CableMessage(
            identifier = CableIdentifier(
                channel = "CEChannel"
            ),
            type = "confirm_subscription"
        )
        val adapter = moshi.adapter(CableMessage::class.java)
        assertEquals(model, adapter.fromJson(json))
        assertEquals(json, adapter.toJson(model))
    }

    @Test
    fun `encode decode confirm subscription p channel`() {
        val json = """{"identifier":"{\"channel\":\"PChannel\",\"m\":\"279\"}",""" +
            """"type":"confirm_subscription"}"""
        val model = CableMessage(
            identifier = CableIdentifier(
                channel = "PChannel",
                coinId = 279
            ),
            type = "confirm_subscription"
        )
        val adapter = moshi.adapter(CableMessage::class.java)
        assertEquals(model, adapter.fromJson(json))
        assertEquals(json, adapter.toJson(model))
    }

    @Test
    fun `encode decode ping`() {
        val json = """{"type":"ping"}""".trimMargin()
        val model = CableMessage(
            type = "ping"
        )
        val adapter = moshi.adapter(CableMessage::class.java)
        assertEquals(model, adapter.fromJson(json))
        assertEquals(json, adapter.toJson(model))
    }

    @Test
    fun `decode price from ce channel`() {
        val json = """{"identifier":"{\"channel\":\"CEChannel\"}","message":{"r":{"usd":"42553.472","vnd":"972772384.207"}}}"""
        val model = CableMessage(
            identifier = CableIdentifier(
                channel = "CEChannel",
            ),
            message = MixedMessage(
                bitcoinPrice = mapOf(
                    "usd" to 42553.472,
                    "vnd" to 972772384.207,
                )
            )
        )
        val adapter = moshi.adapter(CableMessage::class.java)
        assertEquals(model, adapter.fromJson(json))
    }

    @Test
    fun `decode price from p channel`() {
        val json = """{"identifier":"{\"channel\":\"PChannel\",\"m\":\"11757\"}","message":{"c":11757,"e":{"pln":1.794,"rub":1.794},"p":0.00010457250318226457}}"""
        val model = CableMessage(
            identifier = CableIdentifier(
                channel = "PChannel",
                coinId = 11757
            ),
            message = MixedMessage(
                coinId = 11757,
                percent = 0.00010457250318226457
            )
        )
        val adapter = moshi.adapter(CableMessage::class.java)
        assertEquals(model, adapter.fromJson(json))
    }

}
