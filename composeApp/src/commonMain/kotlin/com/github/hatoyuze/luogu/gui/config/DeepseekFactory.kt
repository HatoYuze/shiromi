// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.config

import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.skill.api.LuoguApi
import io.github.hatoyuze.deepseek.protocol.api.ChatConfig
import io.github.hatoyuze.deepseek.protocol.api.Deepseek
import io.github.hatoyuze.deepseek.protocol.api.deepseek
import io.github.hatoyuze.deepseek.protocol.api.entity.ResponseFormat
import io.github.hatoyuze.deepseek.toolcall.dsl.ToolHostBuilder

/**
 * Protocol-level JSON output for COACH sessions (`response_format: {"type": "json_object"}`).
 *
 * DeepSeek JSON mode forces the model to emit a valid JSON object, which makes the
 * coach's `{"progress": ...}` protocol far more reliable than prompt-only constraints.
 * Set to `false` to fall back to prompt constraints + the tolerant [StreamCoachParser]
 * if JSON mode ever suppresses tool-call rounds on a given model.
 */
const val COACH_JSON_MODE = true

/**
 * Builds a [Deepseek] from [ConfigService].
 *
 * apiKey / model / prompt are snapshotted at creation time. Generation parameters:
 * - CHAT shares the live [ConfigService.chatConfig] instance (settings changes apply
 *   to subsequent requests immediately).
 * - COACH uses a snapshot copy so that [ResponseFormat.JSON_OBJECT] can be enabled at
 *   the protocol level without leaking JSON mode into CHAT sessions. Trade-off: COACH
 *   generation parameters are captured at session build time and no longer track
 *   mid-session settings changes (rebuild the session to pick them up).
 */
fun deepseekWithConfig(
    type: SessionType,
    settings: ConfigService,
    api: LuoguApi,
    toolsBlock: ToolHostBuilder.(LuoguApi) -> Unit,
): Deepseek {
    val config = when (type) {
        SessionType.CHAT -> settings.chatConfig
        SessionType.COACH -> snapshotForCoach(settings)
    }
    return deepseek(settings.apiKey, sharedConfig = config) {
        model { custom(settings.model) }
        prompt = when (type) {
            SessionType.CHAT -> settings.chatPrompt
            SessionType.COACH -> settings.coachPrompt
        }
        tools { toolsBlock(api) }
    }
}

/**
 * COACH sessions use a snapshot copy of the shared [ChatConfig] so that
 * [ResponseFormat.JSON_OBJECT] can be enabled at the protocol level without leaking
 * JSON mode into CHAT sessions (which keep the live-shared instance).
 *
 * Trade-off: COACH generation parameters are captured at session build time and no
 * longer track mid-session settings changes (rebuild the session to pick them up).
 *
 * IMPORTANT: keep the copied fields in sync with [ConfigService.apply] — if a new
 * `ChatConfig` field is configured there, it must be copied here too.
 */
private fun snapshotForCoach(settings: ConfigService): ChatConfig = ChatConfig().apply {
    maxTokens = settings.chatConfig.maxTokens
    temperature = settings.chatConfig.temperature
    topP = settings.chatConfig.topP
    thinkingMode = settings.chatConfig.thinkingMode
    maxToolIterations = settings.chatConfig.maxToolIterations
    if (COACH_JSON_MODE) {
        responseFormat = ResponseFormat.JSON_OBJECT
    }
}
