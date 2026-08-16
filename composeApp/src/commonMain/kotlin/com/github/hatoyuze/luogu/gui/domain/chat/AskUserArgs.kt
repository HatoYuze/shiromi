package com.github.hatoyuze.luogu.gui.domain.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parsed arguments of an `askuser` tool call.
 */
data class AskUserArgs(
    val desc: String,
    val timeoutMs: Int,
    val isMulti: Boolean,
    val allowCustom: Boolean,
    val options: List<String>,
)

private val askUserJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Pure parser for `askuser` tool arguments; returns null on malformed JSON. */
fun parseAskUserArgs(arguments: String): AskUserArgs? = try {
    val obj = askUserJson.parseToJsonElement(arguments).jsonObject
    AskUserArgs(
        desc = obj["desc"]?.jsonPrimitive?.contentOrNull ?: "",
        timeoutMs = obj["timeout"]?.jsonPrimitive?.int ?: 60_000,
        isMulti = obj["isMulti"]?.jsonPrimitive?.boolean ?: false,
        allowCustom = obj["allowCustom"]?.jsonPrimitive?.boolean ?: false,
        options = obj["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
    )
} catch (_: Exception) {
    null
}
