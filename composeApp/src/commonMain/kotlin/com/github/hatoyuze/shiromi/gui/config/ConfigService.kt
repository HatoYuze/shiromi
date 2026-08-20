// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.config

import io.github.hatoyuze.deepseek.protocol.api.ChatConfig
import io.github.hatoyuze.deepseek.protocol.api.entity.ThinkingMode

/**
 * Application-wide configuration singleton.
 *
 * Generation parameters (maxTokens / temperature / topP / thinkingMode /
 * maxToolIterations) live directly on [chatConfig], an instance of the root
 * [ChatConfig] shared by every [io.github.hatoyuze.deepseek.protocol.api.Deepseek]
 * session, so UI edits take effect immediately on subsequent requests.
 * Session-scoped fields (apiKey / model / prompts / cookie) are read as a
 * snapshot when a session is created.
 */
object ConfigService {
    var apiKey: String = ""
    var model: String = "deepseek-v4-flash"
    var chatPrompt: String = ""
    var coachPrompt: String = ""
    var luoguCookie: String = ""
    var luoguUid: String = ""
    var remoteModels: List<String>? = null

    val chatConfig: ChatConfig = ChatConfig()

    /** Load persisted configuration into the singleton. */
    fun apply(config: GuiConfig) {
        apiKey = config.apiKey
        model = config.model
        chatPrompt = config.chatPrompt
        coachPrompt = config.coachPrompt
        luoguCookie = config.luoguCookie
        luoguUid = config.luoguUid
        chatConfig.maxTokens = if (config.maxTokens < 0) null else config.maxTokens
        chatConfig.temperature = config.temperature
        chatConfig.topP = config.topP
        chatConfig.thinkingMode = config.thinkingMode
        chatConfig.maxToolIterations = config.maxToolIterations
    }

    /** Export the singleton into a persistable [GuiConfig]. */
    fun toGuiConfig(): GuiConfig = GuiConfig(
        apiKey = apiKey,
        model = model,
        chatPrompt = chatPrompt,
        coachPrompt = coachPrompt,
        luoguCookie = luoguCookie,
        luoguUid = luoguUid,
        maxTokens = chatConfig.maxTokens ?: GuiConfig.UNLIMITED_TOKENS,
        temperature = chatConfig.temperature ?: 0.7,
        topP = chatConfig.topP,
        thinkingMode = chatConfig.thinkingMode ?: ThinkingMode.Enabled,
        maxToolIterations = chatConfig.maxToolIterations,
    )
}
