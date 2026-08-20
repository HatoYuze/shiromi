// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.protocol.coach

import com.github.hatoyuze.shiromi.protocol.api.LuoguApiClient
import com.github.hatoyuze.shiromi.protocol.api.installLuoguTools
import io.github.hatoyuze.deepseek.protocol.api.ChatChunk
import io.github.hatoyuze.deepseek.protocol.api.Deepseek
import io.github.hatoyuze.deepseek.toolcall.dsl.ToolHostBuilder
import io.github.hatoyuze.deepseek.toolcall.dsl.toolHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * 教练会话编排器。
 *
 * 封装 [Deepseek] + [LuoguApi] + [MemoryProvider] 的集成，
 * 提供一键注册全部 6 个工具和带结构化解析的对话方法。
 *
 * ## 使用方式
 *
 * ```kotlin
 * val api = LuoguApi().apply { cookie = "..." }
 * val provider = MyMemoryProvider()
 *
 * val session = CoachSession(
 *     deepseek = deepseek("sk-xxx") { config { maxTokens = 4096 } },
 *     luoguApi = api,
 *     memoryProvider = provider,
 * )
 * session.installAllTools()
 *
 * // 方式一：原始对话（自行解析）
 * session.deepseek.chatStream("学习动态规划").collect { ... }
 *
 * // 方式二：结构化解析（自动提取 JSON 应答为 CoachResponse）
 * session.chatParsed("学习动态规划").collect { response ->
 *     when (response) {
 *         is CoachResponse.Init -> println("选中题目: ${response.selected}")
 *         is CoachResponse.Thinking -> println("引导: ${response.content}")
 *         is CoachResponse.Finished -> println("总结: ${response.summary}")
 *     }
 * }
 * ```
 *
 * @param deepseek 已配置（含 prompt）的 DeepSeek 客户端
 * @param luoguApi 已配置凭证的洛谷 API 客户端
 * @param memoryProvider 学生记忆数据源
 */
class CoachSession(
    val deepseek: Deepseek,
    val luoguApi: LuoguApiClient,
    val memoryProvider: MemoryProvider,
) {

    // ── 工具注册 ──

    /**
     * 一次性注册全部 11 个工具到 [deepseek] 的 [Deepseek.toolHost] 中：
     * - 9 个洛谷爬虫工具（`luogu_*`）
     * - 2 个教练辅助工具（`get_memory` / `get_selected`）
     *
     * 调用此方法后即可通过 [deepseek.chatStream] 开始教练对话。
     */
    fun installAllTools() {
        deepseek.toolHost = toolHost {
            installLuoguTools(luoguApi)
            installCoachTools(memoryProvider)
        }
    }

    // ── 对话接口 ──

    /**
     * 发起教练对话，自动解析 LLM 输出中的 [CoachResponse] JSON。
     *
     * 从 [Deepseek.chatStream] 的流式内容增量中提取完整的 JSON 对象，
     * 反序列化为 [CoachResponse] 子类型后 emit。
     *
     * 无法解析为 [CoachResponse] 的普通文本增量**不会**被 emit
     * （如需原始文本增量，使用 [deepseek.chatStream] 直接订阅）。
     *
     * @param userMessage 用户消息
     * @return 结构化的 [CoachResponse] 流
     */
    fun chatParsed(userMessage: String): Flow<CoachResponse> {
        return deepseek.chatStream(userMessage)
            .mapNotNull { chunk ->
                (chunk as? ChatChunk.ContentDelta)
                    ?.content
                    ?.takeIf { it.isNotEmpty() }
            }
            .parseCoachStream()
            .mapNotNull { segment ->
                (segment as? CoachSegment.Parsed)?.response
            }
    }
}

// ═══════════════════════════════════════════════════════════
// 顶层便捷函数
// ═══════════════════════════════════════════════════════════

/**
 * 一次性将所有洛谷工具和教练工具注册到 [ToolHostBuilder] 中。
 *
 * 等价于依次调用 [installLuoguTools] + [installCoachTools]。
 *
 * ```kotlin
 * val ds = deepseek("sk-xxx") {
 *     tools {
 *         installAllTools(luoguApi, memoryProvider)
 *     }
 * }
 * ```
 */
fun ToolHostBuilder.installAllTools(api: LuoguApiClient, provider: MemoryProvider) {
    installLuoguTools(api)
    installCoachTools(provider)
}

/** Temporary no-arg overload — will be removed in Phase 4 when DeepseekService is refactored. */
@Deprecated("Use installAllTools(api, provider) instead", ReplaceWith("installAllTools(api, provider)"))
fun ToolHostBuilder.installAllTools() {
    // No-op — properly wired version coming in Phase 4
}
