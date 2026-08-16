package com.github.hatoyuze.luogu.gui.presentation.components.askuser

import io.github.hatoyuze.deepseek.toolcall.dsl.ToolHostBuilder
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Registers the [askuser] tool into [ToolHostBuilder].
 *
 * The tool suspends the agent's tool-execution coroutine until the user
 * answers the question (or the timeout expires), then returns the
 * selected options as a JSON string array, or "null" on timeout.
 *
 * Usage from [com.github.hatoyuze.luogu.gui.data.remote.DeepseekChatService]:
 * ```kotlin
 * tools {
 *     installLuoguTools(createLuoguApi())
 *     installAskUserTool(::bridgeAskUser)
 * }
 * ```
 */
fun ToolHostBuilder.installAskUserTool(
    askUser: suspend (desc: String, timeoutMs: Int, isMulti: Boolean, options: List<String>, allowCustom: Boolean) -> String?,
) {
    tool("askuser") {
        description = buildString {
            append("向用户提问，收集用户的选择或输入。")
            append("用于在解题过程中需要了解用户背景、偏好或确认信息时调用。")
            append("若超时未收到用户回答则返回 null。")
        }
        parameters {
            string("desc") { description = "问题描述"; required = true }
            integer("timeout") { description = "超时时间（毫秒），默认 60000（60秒）"; required = false }
            boolean("isMulti", "是否允许多选，默认 false（单选）")
            boolean("allowCustom", "是否允许用户输入自定义文本，默认 false")
            array("options") {
                description = "可选项列表"
                items { string("option") { description = "选项文本" } }
            }
        }
        execute { bag, _ ->
            val desc = bag.getString("desc")
            val timeoutMs = bag["timeout"]?.toString()?.toDoubleOrNull()?.toInt() ?: 60000
            val isMulti = bag["isMulti"].toString().toBooleanStrictOrNull() ?: false
            val options = try {
                bag.getList("options").map { it.toString() }
            } catch (_: Exception) {
                emptyList()
            }
            val allowCustom = bag["allowCustom"].toString().toBooleanStrictOrNull() ?: false

            withTimeoutOrNull(timeoutMs.toLong()) {
                askUser(desc, timeoutMs, isMulti, options, allowCustom)
            } ?: "null"
        }
    }
}
