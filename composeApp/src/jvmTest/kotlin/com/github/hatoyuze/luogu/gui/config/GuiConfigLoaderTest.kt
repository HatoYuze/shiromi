// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package com.github.hatoyuze.luogu.gui.config

import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals

class GuiConfigLoaderTest {

    @Test
    fun saveThenLoad_shouldRoundTrip() {
        val dir = Files.createTempDirectory("luogu-gui-config-test")
        try {
            val loader = GuiConfigLoader(dir.toFile().absolutePath.toPath())
            val config = GuiConfig(
                apiKey = "sk-abc",
                model = "deepseek-v4-pro",
                maxTokens = -1,
                temperature = 0.4,
                topP = 0.8,
                maxToolIterations = 7,
                luoguCookie = "cookie",
                luoguUid = "7",
                chatPrompt = "chat prompt",
                coachPrompt = "coach prompt",
            )
            loader.save(config)
            assertEquals(config, loader.load())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun load_missingFiles_shouldCreateDefaultsAndParse() {
        val dir = Files.createTempDirectory("luogu-gui-config-default-test")
        try {
            val loader = GuiConfigLoader(dir.toFile().absolutePath.toPath())
            val config = loader.load()
            assertEquals("deepseek-v4-flash", config.model)
            assertEquals(4096, config.maxTokens)
            // 默认 prompt 语义：chat 来自打包默认模板，coach 保持为空以便由
            // AppInitializer 从 luogu_agent_prompt.txt 资源兜底。
            assertEquals(
                "You are a helpful AI assistant powered by DeepSeek. Answer questions concisely and accurately.",
                config.chatPrompt,
            )
            assertEquals("", config.coachPrompt)
        } finally {
            dir.deleteRecursively()
        }
    }
}
