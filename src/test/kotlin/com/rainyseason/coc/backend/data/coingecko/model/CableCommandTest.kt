package com.rainyseason.coc.backend.data.coingecko.model

import com.rainyseason.coc.backend.data.RawJsonAdapter
import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CableCommandTest {
    private val moshi = Moshi.Builder()
        .add(RawJsonAdapter)
        .build()

    @Test
    fun `decode encode ce channel correct`() {
        val json = """{"command":"subscribe","identifier":"{\"channel\":\"CEChannel\"}"}"""
        val expected = CableCommand(
            command = "subscribe",
            identifier = CableIdentifier(
                channel = "CEChannel"
            )
        )
        val adapter = moshi.adapter(CableCommand::class.java)
        assertEquals(expected, adapter.fromJson(json))
        assertEquals(json, adapter.toJson(expected))
    }

    @Test
    fun `decode encode pe channel correct`() {
        val json =
            """{"command":"unsubscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"1364\"}"}"""
        val expected = CableCommand(
            command = "unsubscribe",
            identifier = CableIdentifier(
                channel = "PChannel",
                coinId = 1364,
            )
        )
        val adapter = moshi.adapter(CableCommand::class.java)
        assertEquals(expected, adapter.fromJson(json))
        assertEquals(json, adapter.toJson(expected))
    }
}
