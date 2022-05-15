package com.rainyseason.coc.backend.util

object Env {
    val CONFIG_FILE_PATH: String
        get() = requireEnv("CONFIG_FILE_PATH")
}

fun requireEnv(key: String): String {
    val value = System.getenv(key)
    require(!value.isNullOrBlank()) {
        "Missing env: $key"
    }
    return value
}
