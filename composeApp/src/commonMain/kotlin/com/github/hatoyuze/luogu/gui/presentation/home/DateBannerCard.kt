package com.github.hatoyuze.luogu.gui.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.presentation.components.formatMonthChinese
import com.github.hatoyuze.luogu.gui.presentation.components.formatWeekdayShortChinese
import com.github.hatoyuze.luogu.gui.presentation.components.home.HomeCard
import com.github.hatoyuze.luogu.gui.presentation.components.home.InfoPill
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel

/**
 * 日期横幅（对齐设计稿 date-banner）：
 * 大日期 + 「八月 · 周六」+ 年份 + 连续打卡/事件胶囊 + kaomoji + 鼓励语 +
 * 临近事件倒计时。
 */
@Composable
internal fun DateBannerCard(
    state: HomeViewModel.HomeUiState,
    modifier: Modifier = Modifier,
) {
    val displayDate = state.calendarViewState.selectedDate ?: state.today
    val dayEvents = remember(state.calendarEvents, displayDate) {
        state.calendarEvents.count { it.date == displayDate }
    }
    // 未来事件倒计时（置顶优先、按名称去重、最多 2 条）
    val upcoming = remember(state.calendarEvents, displayDate) {
        state.calendarEvents
            .filter { it.date > displayDate }
            .sortedByDescending { it.pinned }
            .distinctBy { it.name }
            .take(2)
    }

    HomeCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayDate.day.toString(),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "${formatMonthChinese(displayDate)} · ${formatWeekdayShortChinese(displayDate)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = displayDate.year.toString(),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.weight(1f))
            InfoPill(text = "🔥 连续 ${state.streakDays} 天", warm = true)
            if (dayEvents > 0) {
                Spacer(Modifier.width(8.dp))
                InfoPill(text = "$dayEvents 个事件", warm = false)
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = state.randomKaomoji,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
                Text(
                    text = state.encouragementText,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    maxLines = 1,
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                upcoming.forEach { event ->
                    val daysUntil = event.date.toEpochDays() - displayDate.toEpochDays()
                    Text(
                        text = "距 ${event.name} 还剩 $daysUntil 天",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                }
            }
        }
    }
}
