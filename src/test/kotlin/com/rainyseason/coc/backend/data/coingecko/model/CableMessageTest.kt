package com.rainyseason.coc.backend.data.coingecko.model

import com.rainyseason.coc.backend.data.RawJsonAdapter
import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.*
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
    fun `encode decode p channel`() {
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

    // TODO replace with ping message
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


}
