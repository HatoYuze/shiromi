// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:OptIn(io.github.hatoyuze.deepseek.protocol.api.ExperimentalDeepseekApi::class)

package com.github.hatoyuze.luogu.gui.config

import com.github.hatoyuze.luogu.gui.platform.Crypto
import com.github.hatoyuze.luogu.gui.platform.defaultConfigDir
import com.github.hatoyuze.luogu.gui.platform.systemFileSystem
import io.github.hatoyuze.deepseek.protocol.api.entity.ThinkingMode
import okio.Buffer
import okio.FileSystem
import okio.Path

/**
 * Multiplatform [AppConfigStore] implementation.
 *
 * Reads/writes `api_setting.toml` and `agent_prompt.toml` under
 * [defaultConfigDir] (i.e. `dataPath/config`), seeding first-launch files from
 * the embedded default templates. Sensitive fields are obfuscated with [Crypto].
 */
class GuiConfigLoader(
    private val configDir: Path = defaultConfigDir(),
) : AppConfigStore {

    private val fileSystem: FileSystem = systemFileSystem

    override fun load(): GuiConfig {
        fileSystem.createDirectories(configDir, mustCreate = false)
        val apiToml = parseToml(loadOrCreate("api_setting.toml"))
        val api = apiToml["deepseek"]
        val luogu = apiToml["luogu"]
        val prompts = parseToml(loadOrCreate("agent_prompt.toml"))
        val chatPrompt = prompts["chat"]?.get("system_prompt").orEmpty()
        val coachPrompt = prompts["coach"]?.get("system_prompt").orEmpty()
        return GuiConfig(
            apiKey = Crypto.decrypt(api?.get("api_key").orEmpty()),
            model = api?.get("model") ?: "deepseek-v4-flash",
            maxTokens = api?.get("max_tokens")?.toIntOrNull() ?: 4096,
            temperature = api?.get("temperature")?.toDoubleOrNull() ?: 0.7,
            topP = api?.get("top_p")?.toDoubleOrNull(),
            thinkingMode = GuiConfig.parseReasoningEffort(
                api?.get("thinking_mode") ?: api?.get("reasoning_effort"),
            ),
            maxToolIterations = api?.get("max_tool_iterations")?.toIntOrNull() ?: 5,
            luoguCookie = Crypto.decrypt(luogu?.get("cookie").orEmpty()),
            luoguUid = luogu?.get("uid") ?: "",
            chatPrompt = chatPrompt,
            coachPrompt = coachPrompt,
        )
    }

    override fun save(config: GuiConfig) {
        fileSystem.createDirectories(configDir, mustCreate = false)
        writeText(configDir / "api_setting.toml", apiToml(config))
        writeText(configDir / "agent_prompt.toml", promptToml(config))
    }

    private fun apiToml(config: GuiConfig): String = buildString {
        appendLine("[deepseek]")
        appendLine("api_key = \"${Crypto.encrypt(config.apiKey)}\"")
        appendLine("model = \"${config.model}\"")
        appendLine("max_tokens = ${config.maxTokens}")
        appendLine("temperature = ${config.temperature}")
        config.topP?.let { appendLine("top_p = $it") }
        when (val mode = config.thinkingMode) {
            ThinkingMode.Enabled -> Unit
            ThinkingMode.Disabled -> appendLine("thinking_mode = \"disabled\"")
            is ThinkingMode.WithEffort -> appendLine("reasoning_effort = \"${mode.effort.name.lowercase()}\"")
        }
        appendLine("max_tool_iterations = ${config.maxToolIterations}")
        appendLine()
        appendLine("[luogu]")
        appendLine("cookie = \"${Crypto.encrypt(config.luoguCookie)}\"")
        appendLine("uid = ${config.luoguUid.ifBlank { "0" }}")
    }

    private fun promptToml(config: GuiConfig): String = buildString {
        appendLine("[chat]")
        appendLine("system_prompt = \"\"\"")
        appendLine(config.chatPrompt)
        appendLine("\"\"\"")
        appendLine()
        appendLine("[coach]")
        appendLine("system_prompt = \"\"\"")
        appendLine(config.coachPrompt)
        appendLine("\"\"\"")
    }

    private fun loadOrCreate(filename: String): String {
        val file = configDir / filename
        if (fileSystem.exists(file)) return readText(file)
        val default = when (filename) {
            "api_setting.toml" -> DEFAULT_API_SETTING_TOML
            "agent_prompt.toml" -> DEFAULT_AGENT_PROMPT_TOML
            else -> return ""
        }
        writeText(file, default)
        return default
    }

    private fun readText(path: Path): String {
        val source = fileSystem.source(path)
        try {
            val buffer = Buffer()
            buffer.writeAll(source)
            return buffer.readUtf8()
        } finally {
            source.close()
        }
    }

    private fun writeText(path: Path, content: String) {
        val sink = fileSystem.sink(path)
        try {
            val buffer = Buffer().apply { writeUtf8(content) }
            sink.write(buffer, buffer.size)
        } finally {
            sink.close()
        }
    }

    private fun parseToml(content: String): Map<String, Map<String, String>> {
        val result = linkedMapOf<String, LinkedHashMap<String, String>>()
        var section = ""
        var multilineKey: String? = null
        val multiline = StringBuilder()
        for (rawLine in content.lines()) {
            if (multilineKey != null) {
                if (rawLine.trim() == "\"\"\"") {
                    result.getOrPut(section) { linkedMapOf() }[multilineKey] = multiline.toString().trimEnd('\n')
                    multilineKey = null
                } else {
                    multiline.appendLine(rawLine)
                }
                continue
            }
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim()
                result.getOrPut(section) { linkedMapOf() }
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (value.startsWith("\"\"\"")) {
                multilineKey = key
                multiline.clear()
                val rest = value.removePrefix("\"\"\"").trim()
                if (rest.isNotEmpty()) multiline.appendLine(rest)
                continue
            }
            result.getOrPut(section) { linkedMapOf() }[key] = parseValue(value)
        }
        return result
    }

    private fun parseValue(raw: String): String = when {
        raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
            raw.substring(1, raw.length - 1)
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
        raw.length >= 2 && raw.startsWith("'") && raw.endsWith("'") ->
            raw.substring(1, raw.length - 1)
        else -> raw.substringBefore('#').trim()
    }

    companion object {
        private const val TQ = "\"\"\""

        /** First-launch template for `api_setting.toml`. */
        internal val DEFAULT_API_SETTING_TOML = """
            [deepseek]
            api_key = ""
            model = "deepseek-v4-flash"
            max_tokens = 4096
            temperature = 0.7
            max_tool_iterations = 5

            [luogu]
            cookie = ""
            uid = 0
        """.trimIndent()

        /** First-launch template for `agent_prompt.toml` (built via concat: TOML `"""` cannot nest in Kotlin raw strings). */
        internal val DEFAULT_AGENT_PROMPT_TOML = buildString {
            appendLine("[chat]")
            appendLine("system_prompt = $TQ")
            appendLine("You are a helpful AI assistant powered by DeepSeek. Answer questions concisely and accurately.")
            appendLine(TQ)
            appendLine()
            appendLine("[coach]")
            appendLine("system_prompt = $TQ")
            appendLine(TQ)
        }
    }
}
