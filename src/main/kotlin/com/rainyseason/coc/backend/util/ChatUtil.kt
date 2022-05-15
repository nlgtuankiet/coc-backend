package com.rainyseason.coc.backend.util

object ChatUtil {
    fun getChatId(user1: String, user2: String): String {
        return arrayOf(user1, user2).sortedArray().joinToString(separator = "_")
    }
}
