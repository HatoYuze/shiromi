// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.luogu.gui.presentation.components.DailyProblemCard
import com.github.hatoyuze.luogu.gui.presentation.components.HomeDesignTokens
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel
import kotlinx.datetime.LocalDate

/**
 * 桌面首页主内容（对齐设计稿「布局 3 优化版 A」）：
 * 主列（日期横幅 → 每日推荐|学习进度 → 搜索|本周概览）+ 信息轨（迷你日历 + 待办预览）。
 * 宽窗口（内容区 ≥ [HomeDesignTokens.RailBreakpoint]）信息轨并排；窄窗口折叠到主列下方。
 */
@Composable
internal fun HomeLayout(
    state: HomeViewModel.HomeUiState,
    onNavigateMonth: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNavigateToToday: () -> Unit,
    onAddTodo: (String, Long?) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onAddEvent: (String, LocalDate, Int, Boolean, Boolean, Int?) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onUpdateTopic: (String, Int) -> Unit,
    onRefreshDailyProblem: () -> Unit,
    onViewProblemDetail: (String) -> Unit,
    showOverlay: (@Composable () -> Unit) -> Unit,
    hideOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(22.dp))

            // 主列（日期横幅 + 2×2 卡片）
            val mainColumn = @Composable {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(HomeDesignTokens.RowSpacing),
                ) {
                    DateBannerCard(state = state)

                    // 每日推荐 | 学习进度
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(HomeDesignTokens.RowSpacing),
                    ) {
                        DailyProblemCard(
                            state = state.dailyProblemState,
                            onRefresh = onRefreshDailyProblem,
                            onViewDetail = onViewProblemDetail,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        ProgressCard(
                            state = state,
                            onUpdateTopic = onUpdateTopic,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }

                    // 搜索 | 本周概览
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HomeDesignTokens.RowSpacing),
                    ) {
                        SearchCard(
                            onSearchProblem = onViewProblemDetail,
                            modifier = Modifier.weight(1f),
                        )
                        WeeklyOverviewCard(
                            state = state,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 信息轨（迷你日历 + 待办预览）
            val rail = @Composable {
                Column(
                    modifier = Modifier.width(HomeDesignTokens.RailWidth),
                    verticalArrangement = Arrangement.spacedBy(HomeDesignTokens.RowSpacing),
                ) {
                    MiniCalendarCard(
                        state = state,
                        onNavigateMonth = onNavigateMonth,
                        onSelectDate = onSelectDate,
                        onNavigateToToday = onNavigateToToday,
                        onAddEvent = onAddEvent,
                        onDeleteEvent = onDeleteEvent,
                        showOverlay = { content -> showOverlay(content) },
                        hideOverlay = { hideOverlay() },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TodoPreviewCard(
                        state = state,
                        onAddTodo = onAddTodo,
                        onToggleTodo = onToggleTodo,
                        onDeleteTodo = onDeleteTodo,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 宽窗口：主列 + 信息轨并排；窄窗口：信息轨折叠到主列下方
            if (maxWidth >= HomeDesignTokens.RailBreakpoint) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HomeDesignTokens.RowSpacing),
                ) {
                    Column(modifier = Modifier.weight(1f)) { mainColumn() }
                    rail()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(HomeDesignTokens.RowSpacing)) {
                    mainColumn()
                    rail()
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
