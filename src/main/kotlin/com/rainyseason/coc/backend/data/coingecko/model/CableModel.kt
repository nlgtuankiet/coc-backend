package com.rainyseason.coc.backend.data.coingecko.model

import com.rainyseason.coc.backend.data.RawJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * {"command":"subscribe","identifier":"{\"channel\":\"CEChannel\"}"}
 * {"command":"unsubscribe","identifier":"{\"channel\":\"PChannel\",\"m\":\"1364\"}"}
 *
 * @param command subscribe or  unsubscribe
 */
@JsonClass(generateAdapter = true)
data class CableCommand(
    val command: String,
    @RawJson
    val identifier: CableIdentifier,
)

/**
 * @param channel CEChannel or PChannel
 */
@JsonClass(generateAdapter = true)
data class CableIdentifier(
    val channel: String,
    @RawJson
    @Json(name = "m")
    val coinId: Int? = null,
)

/**
 *
 *
 * @param type welcome, ping, confirm_subscription, reject_subscription
 * @param message
 */
@JsonClass(generateAdapter = true)
data class CableMessage(
    @RawJson
    val identifier: CableIdentifier? = null,
    val type: String? = null,
    val message: ComplexMessage? = null,
)

sealed class ComplexMessage

@JsonClass(generateAdapter = true)
data class PriceMessage(
    @Json(name = "c")
    val coinId: Int? = null,
    @Json(name = "p")
    val percent: Double? = null,
    @Json(name = "r")
    val bitcoinPrice: Map<String, Double>? = null, // currency to price
) : ComplexMessage()

data class LongMessage(val value: Long) : ComplexMessage()

class ComplexMessageJsonAdapter(
    moshi: Moshi,
) : JsonAdapter<ComplexMessage>() {
    private val mixedMessageAdapter = moshi.adapter(PriceMessage::class.java)
    override fun fromJson(reader: JsonReader): ComplexMessage? {
        return when (val token = reader.peek()) {
            JsonReader.Token.NULL -> {
                reader.nextNull()
            }
            JsonReader.Token.NUMBER -> {
                LongMessage(reader.nextLong())
            }
            else -> {
                mixedMessageAdapter.fromJson(reader)
            }
        }
    }

    override fun toJson(writer: JsonWriter, value: ComplexMessage?) {
        when (value) {
            null -> {
                writer.nullValue()
            }
            is LongMessage -> {
                writer.jsonValue(value.value)
            }
            is PriceMessage -> {
                mixedMessageAdapter.toJson(writer, value)
            }
        }
    }

    companion object : Factory {
        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi,
        ): JsonAdapter<*>? {
            if (Types.getRawType(type) != ComplexMessage::class.java) {
                return null
            }
            return ComplexMessageJsonAdapter(moshi)
        }
    }
}
