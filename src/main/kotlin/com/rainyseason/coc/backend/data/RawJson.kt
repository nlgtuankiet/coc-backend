package com.rainyseason.coc.backend.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class RawJson

class RawJsonAdapter<T>(
    private val originAdapter: JsonAdapter<T>,
) : JsonAdapter<T>() {
    override fun fromJson(reader: JsonReader): T? {
        return if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull()
        } else {
            originAdapter.fromJson(reader.nextString())
        }
    }

    override fun toJson(writer: JsonWriter, value: T?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(originAdapter.toJson(value))
        }
    }

    companion object : Factory {
        override fun create(
            type: Type,
            annotations: MutableSet<out Annotation>,
            moshi: Moshi,
        ): JsonAdapter<*>? {
            val nextAnnotations = Types.nextAnnotations(annotations, RawJson::class.java)
            return if (nextAnnotations == null || nextAnnotations.isNotEmpty()) {
                null
            } else {
                RawJsonAdapter(moshi.nextAdapter<Any>(this, type, nextAnnotations))
            }
        }
    }
}
