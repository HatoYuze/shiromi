@file:OptIn(io.github.hatoyuze.deepseek.protocol.api.ExperimentalDeepseekApi::class)

package com.github.hatoyuze.luogu.gui.config

import io.github.hatoyuze.deepseek.protocol.api.entity.ReasoningEffort
import io.github.hatoyuze.deepseek.protocol.api.entity.ThinkingMode

/**
 * GUI-owned application configuration, decoupled from any CLI config module.
 *
 * [thinkingMode] maps directly to the root [ThinkingMode] type so no parallel
 * generation-parameter representation exists in the GUI.
 */
data class GuiConfig(
    val apiKey: String = "",
    val model: String = "deepseek-v4-flash",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double? = null,
    val thinkingMode: ThinkingMode = ThinkingMode.Enabled,
    val maxToolIterations: Int = 5,
    val luoguCookie: String = "",
    val luoguUid: String = "",
    val chatPrompt: String = "",
    val coachPrompt: String = "",
) {
    companion object {
        /** Unbounded token limit marker used by the TOML round-trip. */
        const val UNLIMITED_TOKENS = -1

        fun parseReasoningEffort(value: String?): ThinkingMode = when {
            value.equals("disabled", ignoreCase = true) -> ThinkingMode.Disabled
            value.equals("high", ignoreCase = true) -> ThinkingMode.WithEffort(ReasoningEffort.HIGH)
            value.equals("max", ignoreCase = true) -> ThinkingMode.WithEffort(ReasoningEffort.MAX)
            else -> ThinkingMode.Enabled
        }

        fun reasoningEffortName(mode: ThinkingMode): String? = when (mode) {
            ThinkingMode.Enabled, ThinkingMode.Disabled -> null
            is ThinkingMode.WithEffort -> mode.effort.name.lowercase()
        }
    }
}
