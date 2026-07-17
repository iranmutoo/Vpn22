package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TelegramResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdateResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "result") val result: List<TelegramUpdate>? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdate(
    @Json(name = "update_id") val updateId: Int,
    @Json(name = "message") val message: TelegramMessage? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    @Json(name = "message_id") val messageId: Int,
    @Json(name = "chat") val chat: TelegramChat,
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChat(
    @Json(name = "id") val id: Long,
    @Json(name = "type") val type: String
)
