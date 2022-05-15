package com.rainyseason.coc.backend.data.firebase

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FirestoreEvent(
    val createTime: Long,
    val exists: Boolean,
    val id: String,
    val readTime: Long,
    val refPath: String,
    val data: Map<String, Any>,
)
