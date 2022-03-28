package com.rainyseason.coc.backend.data.coingecko.model

import com.rainyseason.coc.backend.data.RawJsonAdapter
import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class CableIdentifierTest {
    private val moshi = Moshi.Builder()
        .add(RawJsonAdapter)
        .build()

    @Test
    fun `encode decode correct`() {
        val model = CableIdentifier(
            "CEChannel",
            279
        )
        val adapter = moshi.adapter(CableIdentifier::class.java)
        val json = adapter.toJson(model)
        assertEquals("""{"channel":"CEChannel","m":"279"}""", json)
        assertEquals(model, adapter.fromJson(json))
    }

    @Test
    fun `encode decode null`() {
        val model = CableIdentifier(
            "CEChannel",
        )
        val adapter = moshi.adapter(CableIdentifier::class.java)
        val json = adapter.toJson(model)
        assertEquals("""{"channel":"CEChannel"}""", json)
        assertEquals(model, adapter.fromJson(json))
    }
}
