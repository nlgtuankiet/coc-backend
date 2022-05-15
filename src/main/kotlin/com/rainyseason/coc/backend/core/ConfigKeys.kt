package com.rainyseason.coc.backend.core

import org.apache.commons.configuration2.ImmutableConfiguration

object ConfigKeys {
    val FirestoreForwardPassword = StringConfigKey("firestoreForwardPassword")
    val FirebaseAdminUid = StringConfigKey("firebaseAdminUid")
    val TelegramBotToken = StringConfigKey("telegramBotToken")
    val TelegramBotAndAdminChatId = LongConfigKey("telegramBotAndAdminChatId")
    val Host = StringConfigKey("host")

    fun checkAll(config: ImmutableConfiguration) {
        listOf(
            FirestoreForwardPassword,
            FirebaseAdminUid,
            TelegramBotToken,
            TelegramBotAndAdminChatId,
            Host,
        ).forEach { key ->
            when (key) {
                is StringConfigKey -> config.getValue(key)
                is LongConfigKey -> config.getValue(key)
                else -> error("Unknown type")
            }
        }
    }
}

@JvmInline
value class StringConfigKey(val value: String)

@JvmInline
value class LongConfigKey(val value: String)

fun ImmutableConfiguration.getValue(key: StringConfigKey): String {
    val value = getString(key.value)
    require(!value.isNullOrBlank()) {
        "invalid key: $key value: $value"
    }
    return value
}

fun ImmutableConfiguration.getValue(key: LongConfigKey): Long {
    return getLong(key.value)
}
