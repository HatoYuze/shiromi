// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.home

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.hatoyuze.shiromi.gui.data.remote.DailyProblemAgent
import com.github.hatoyuze.shiromi.gui.domain.model.CalendarEvent
import com.github.hatoyuze.shiromi.gui.domain.model.ChatSessionDomainModel
import com.github.hatoyuze.shiromi.gui.domain.model.DailyProblemResult
import com.github.hatoyuze.shiromi.gui.domain.model.SessionType
import com.github.hatoyuze.shiromi.gui.domain.model.StudyTopic
import com.github.hatoyuze.shiromi.gui.domain.model.TodoItemDomainModel
import com.github.hatoyuze.shiromi.gui.presentation.state.CalendarViewState
import com.github.hatoyuze.shiromi.gui.presentation.state.HomeViewModel
import com.github.hatoyuze.shiromi.gui.theme.LuoguTheme
import com.github.hatoyuze.shiromi.protocol.api.ProblemDetail
import com.github.hatoyuze.shiromi.protocol.api.ProblemDetailData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

/**
 * 桌面首页布局的无头渲染冒烟测试（Compose Desktop ui-test，无需 X 服务）。
 *
 * 断言「布局 3 优化版 A」的关键区块齐全；设置系统属性
 * `-Dshiromi.screenshot.dir=/path` 时把渲染结果导出为 PNG（视觉验证用）。
 */
@OptIn(ExperimentalTestApi::class)
class HomeLayoutRenderTest {

    private val nowMs: Long = Clock.System.now().toEpochMilliseconds()

    private fun sampleState(): HomeViewModel.HomeUiState {
        val today = LocalDate(2026, 8, 17)
        val hourMs = 3_600_000L
        return HomeViewModel.HomeUiState(
            sessions = listOf(
                ChatSessionDomainModel("s1", "P1234 题解讨论", SessionType.CHAT, nowMs, nowMs),
                ChatSessionDomainModel("s2", "动态规划专项训练", SessionType.CHAT, nowMs - hourMs, nowMs - hourMs),
                ChatSessionDomainModel("s3", "图论最短路答疑", SessionType.COACH, nowMs - 2 * hourMs, nowMs - 2 * hourMs),
            ),
            todos = listOf(
                TodoItemDomainModel("t1", "补线段树总结", false, nowMs),
                TodoItemDomainModel("t2", "注册 CF div2 比赛", false, nowMs, dueAt = nowMs + 24 * hourMs),
                TodoItemDomainModel("t3", "P1234 写完并提交", false, nowMs, dueAt = nowMs + 2 * hourMs),
                TodoItemDomainModel("t4", "整理错题本 · 图论", true, nowMs, dueAt = nowMs + 2 * hourMs),
                TodoItemDomainModel("t5", "复习容斥原理例题", false, nowMs - hourMs),
            ),
            recommendations = listOf("结合近期「区间DP」正确率，推荐这道分段最优化题。"),
            today = today,
            calendarViewState = CalendarViewState(
                displayedYear = 2026,
                displayedMonth = 8,
                selectedDate = today,
            ),
            activeDates = setOf(today, LocalDate(2026, 8, 16)),
            streakDays = 12,
            studyTopic = StudyTopic(name = "动态规划", currentCount = 12, goalCount = 20),
            calendarEvents = listOf(
                CalendarEvent(
                    id = "e1", name = "周赛补题复盘", date = LocalDate(2026, 8, 16),
                    createdAtMs = nowMs, color = 0xFFD98F68.toInt(), pinned = true,
                    allDay = false, timeMinutes = 14 * 60,
                ),
                CalendarEvent(
                    id = "e2", name = "数学专题：容斥原理", date = LocalDate(2026, 8, 16),
                    createdAtMs = nowMs, color = 0xFF4A5599.toInt(), allDay = true,
                ),
                CalendarEvent(
                    id = "e3", name = "蓝桥杯报名", date = LocalDate(2026, 8, 21),
                    createdAtMs = nowMs,
                ),
            ),
            dailyProblemState = DailyProblemAgent.DailyProblemState(
                result = DailyProblemResult(
                    pid = "P1234",
                    reason = "结合你近期「区间DP」的正确率，推荐这道分段最优化题，先想 O(n²) 再尝试优化到 O(n log n)。",
                    tips = listOf("先想 O(n²) 再优化"),
                ),
                problemDetail = ProblemDetailData(
                    ProblemDetail(pid = "P1234", name = "数列分段 Section II", tags = listOf(43, 55), difficulty = 4),
                ),
            ),
            randomKaomoji = "(´｡• ᵕ •｡`)",
            encouragementText = "学如逆水行舟",
        )
    }

    @Test
    fun homeLayout_rendersAllDesignSections() = runDesktopComposeUiTest(width = 1180, height = 860) {
        setContent {
            LuoguTheme {
                HomeLayout(
                    state = sampleState(),
                    onNavigateMonth = {},
                    onSelectDate = {},
                    onNavigateToToday = {},
                    onAddTodo = { _, _ -> },
                    onToggleTodo = { _, _ -> },
                    onDeleteTodo = {},
                    onAddEvent = { _, _, _, _, _, _ -> },
                    onDeleteEvent = {},
                    onUpdateTopic = { _, _ -> },
                    onRefreshDailyProblem = {},
                    onViewProblemDetail = {},
                    showOverlay = {},
                    hideOverlay = {},
                )
            }
        }

        // ── 布局 3A 关键区块（HomeLayout = 主列 + 信息轨；侧栏在 HomeScreen 层）──
        onNodeWithText("八月 · 周一").assertExists()         // 日期横幅（2026-08-17 为周一）
        onNodeWithText("连续 12 天").assertExists()      // 打卡胶囊
        onNodeWithText("距 蓝桥杯报名", substring = true).assertExists()  // 临近事件倒计时
        onNodeWithText("每日推荐").assertExists()           // 主列卡 1
        onNodeWithText("P1234 数列分段 Section II").assertExists()
        onNodeWithText("查看详情 ›").assertExists()
        onNodeWithText("学习进度").assertExists()         // 主列卡 2
        onNodeWithText("连续打卡", substring = true).assertExists()
        onNodeWithText("动态规划").assertExists()           // 学习专题名
        onNodeWithText("本周概览").assertExists()         // 主列卡 4
        onNodeWithText("本周待办完成率").assertExists()
        onNodeWithText("待办").assertExists()            // 信息轨
        onNodeWithText("查看全部 ›").assertExists()         // 待办卡（>4 条时展开入口）
        onNodeWithText("2026年 8月").assertExists()      // 信息轨日历标题
        onNodeWithText("有事件").assertExists()             // 日历事件图例

        // ── 视觉验证：-Pshiromi.screenshot.dir=/path 时导出 PNG ──
        val dir = System.getProperty("shiromi.screenshot.dir")?.takeIf { it.isNotBlank() }
        if (dir != null) {
            val image = onRoot().captureToImage()
            val bytes = image.asSkiaBitmap().let { skia ->
                org.jetbrains.skia.Image.makeFromBitmap(skia)
                    .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
            }
            File(dir).mkdirs()
            File(dir, "home-layout-3a.png").writeBytes(bytes)
            assertTrue(File(dir, "home-layout-3a.png").length() > 0, "screenshot written")
        }
    }
}
