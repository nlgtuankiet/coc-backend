package com.rainyseason.coc.backend.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RawJsonTest {
    private val moshi = Moshi.Builder().add(RawJsonAdapter)
        .build()

    @JsonClass(generateAdapter = true)
    data class Info(
        val command: String,
        @RawJson
        val payload: Payload?,
    )

    @JsonClass(generateAdapter = true)
    data class Payload(
        val name: String,
        val count: Int,
    )

    @Test
    fun `encode decode correct`() {
        val input = """{"command":"run","payload":"{\"name\":\"a\",\"count\":1}"}"""
        val expected = Info(
            command = "run",
            payload = Payload(name = "a", count = 1)
        )
        val adapter = moshi.adapter(Info::class.java)
        val decodedObject = adapter.fromJson(input)
        assertEquals(expected, decodedObject)
        val encodedJson = adapter.toJson(expected)
        assertEquals(input, encodedJson)
    }

    @Test
    fun `encode decode null correct`() {
        val inputWithName = """{"command":"run","payload":null}"""
        val inputWithoutName = """{"command":"run"}"""
        val expected = Info(
            command = "run",
            payload = null,
        )
        val adapter = moshi.adapter(Info::class.java)

        val decodedObjectWithName = adapter.fromJson(inputWithName)
        assertEquals(expected, decodedObjectWithName)

        val decodedObjectWithoutName = adapter.fromJson(inputWithoutName)
        assertEquals(expected, decodedObjectWithoutName)

        val encodedJson = adapter.toJson(expected)
        assertEquals(inputWithoutName, encodedJson)
    }
}
