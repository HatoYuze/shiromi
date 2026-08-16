package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

// ═══════════════════════════════════════════════════════════════
// Chinese date formatting helpers
// ═══════════════════════════════════════════════════════════════

private val MONTH_NAMES = listOf(
    "", "一月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "十一月", "十二月"
)

private val WEEKDAY_NAMES = listOf(
    "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"
)

fun formatMonthChinese(date: LocalDate): String = MONTH_NAMES[date.month.number]

fun formatWeekdayChinese(date: LocalDate): String {
    // kotlinx-datetime: Monday=0
    return WEEKDAY_NAMES[date.dayOfWeek.ordinal]
}

// ═══════════════════════════════════════════════════════════════
// DateDisplayBlock — Traditional Chinese Calendar Style
// ═══════════════════════════════════════════════════════════════

@Composable
fun DateDisplayBlock(
    displayDate: LocalDate,
    isToday: Boolean,
    events: List<CalendarEvent>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top row: month(vertical) + number + weekday(vertical) ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                // LEFT: True vertical month text (Column of chars, top→bottom)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    formatMonthChinese(displayDate).forEach { char ->
                        Text(
                            text = char.toString(),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.width(20.dp))

                // CENTER: Huge day number
                Text(
                    text = displayDate.day.toString().padStart(2, '0'),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.tertiary,
                    lineHeight = 96.sp,
                )

                Spacer(Modifier.width(20.dp))

                // RIGHT: True vertical weekday (Column of chars, top→bottom)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    formatWeekdayChinese(displayDate).forEach { char ->
                        Text(
                            text = char.toString(),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Bottom: countdowns / celebration ──
            if (events.isEmpty()) {
                Text(
                    "右击日历标记重要日期",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            } else {
                // Today's events (daysUntil == 0)
                val todayEvents = events.filter { it.date == displayDate }
                todayEvents.forEach { event ->
                    val nameColor = if (event.color != 0) Color(event.color) else MaterialTheme.colorScheme.secondary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text("🎉 今天是 ", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Text(event.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = nameColor)
                        Text(" 的日子.", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }

                // Upcoming countdowns (pinned-first from DB, name-deduplicated)
                val upcoming = events
                    .filter { it.date > displayDate }
                    .distinctBy { it.name }
                    .take(3)
                upcoming.forEach { event ->
                    val daysUntil = event.date.toEpochDays() - displayDate.toEpochDays()
                    val nameColor = if (event.color != 0) Color(event.color) else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        if (event.pinned) {
                            Icon(
                                PinIcon,
                                contentDescription = "已置顶",
                                modifier = Modifier.size(12.dp),
                                tint = Color.Black,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            "距 ",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            event.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = nameColor,
                        )
                        Text(
                            " 还剩 ${daysUntil} 天",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }

                if (todayEvents.isEmpty() && upcoming.isEmpty()) {
                    Text(
                        "所有事件已过",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}
