// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.hatoyuze.luogu.gui.presentation.components.CalendarPanel
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel
import kotlinx.datetime.LocalDate

/**
 * 信息轨迷你日历（对齐设计稿 rail 日历）：
 * CalendarPanel 的桌面配置 —— 周一优先表头 + 事件日图例胶囊 + 今天按钮。
 */
@Composable
internal fun MiniCalendarCard(
    state: HomeViewModel.HomeUiState,
    onNavigateMonth: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNavigateToToday: () -> Unit,
    onAddEvent: (String, LocalDate, Int, Boolean, Boolean, Int?) -> Unit,
    onDeleteEvent: (String) -> Unit,
    showOverlay: (@Composable () -> Unit) -> Unit,
    hideOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CalendarPanel(
        displayedYear = state.calendarViewState.displayedYear,
        displayedMonth = state.calendarViewState.displayedMonth,
        selectedDate = state.calendarViewState.selectedDate,
        today = state.today,
        activeDates = state.activeDates,
        events = state.calendarEvents,
        onNavigateMonth = onNavigateMonth,
        onSelectDate = onSelectDate,
        onNavigateToToday = onNavigateToToday,
        onAddEvent = onAddEvent,
        onDeleteEvent = onDeleteEvent,
        showOverlay = { content -> showOverlay(content) },
        hideOverlay = { hideOverlay() },
        modifier = modifier,
        mondayFirst = true,
        showLegend = true,
    )
}
