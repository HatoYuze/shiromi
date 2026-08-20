// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.shiromi.gui.domain.model.CalendarEvent
import com.github.hatoyuze.shiromi.gui.presentation.components.icons.AppIcons
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronLeft
import compose.icons.feathericons.ChevronRight
import kotlin.time.TimeSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

// ═══════════════════════════════════════════════════════════
// Calendar helpers
// ═══════════════════════════════════════════════════════════

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 30
}

fun firstDayOffset(year: Int, month: Int, mondayFirst: Boolean = false): Int {
    val firstDay = LocalDate(year, month, 1)
    // kotlinx-datetime ordinal：MONDAY=0 … SUNDAY=6
    return if (mondayFirst) {
        // 周一优先：周一在第 0 列
        firstDay.dayOfWeek.ordinal
    } else {
        // 周日优先：周日在第 0 列
        (firstDay.dayOfWeek.ordinal + 1) % 7
    }
}

private val WEEKDAY_HEADERS_SUNDAY_FIRST = listOf("日", "一", "二", "三", "四", "五", "六")
private val WEEKDAY_HEADERS_MONDAY_FIRST = listOf("一", "二", "三", "四", "五", "六", "日")

/** 双击判定窗口：两次点击间隔小于该值视为双击（新建/编辑事件）。 */
private const val DOUBLE_TAP_WINDOW_MS = 400L

// ═══════════════════════════════════════════════════════════
// CalendarGrid — 纯网格（无表头/无弹窗逻辑，桌面与移动端共用）
// ═══════════════════════════════════════════════════════════

/**
 * 月历纯网格：星期表头 + 日期单元（选中/今天/事件点/活动点），
 * 支持单击选中、双击与右键（[onEditDate]）编辑事件。
 *
 * @param mondayFirst 周一优先表头（一~日）；否则周日优先（日~六）。
 * @param compactSelected 移动端样式：选中日 = 实心主色圆 + 白字。
 */
@Composable
fun CalendarGrid(
    displayedYear: Int,
    displayedMonth: Int,
    selectedDate: LocalDate?,
    today: LocalDate,
    activeDates: Set<LocalDate>,
    events: List<CalendarEvent>,
    mondayFirst: Boolean = false,
    compactSelected: Boolean = false,
    onSelectDate: (LocalDate) -> Unit,
    onEditDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = daysInMonth(displayedYear, displayedMonth)
    val offset = firstDayOffset(displayedYear, displayedMonth, mondayFirst)
    val weekdayHeaders = if (mondayFirst) WEEKDAY_HEADERS_MONDAY_FIRST else WEEKDAY_HEADERS_SUNDAY_FIRST

    // 事件日期 → 颜色 查找表
    val eventDates = remember(events) { events.map { it.date }.toSet() }
    val eventColorMap = remember(events) { events.associate { it.date to it.color } }

    Column(modifier = modifier) {
        // ── Weekday headers ──
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayHeaders.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Day grid (fixed 36dp cells, no aspectRatio) ──
        val totalCells = offset + days
        val numRows = (totalCells + 6) / 7
        for (row in 0 until numRows) {
            Row(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                for (col in 0..6) {
                    val index = row * 7 + col
                    val dayNum = index - offset + 1

                    if (dayNum in 1..days) {
                        val date = LocalDate(displayedYear, displayedMonth, dayNum)
                        CalendarDayCell(
                            dayNum = dayNum,
                            date = date,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            hasActivity = date in activeDates,
                            hasEvent = date in eventDates,
                            eventColorInt = eventColorMap[date] ?: 0,
                            compactSelected = compactSelected,
                            onClick = { onSelectDate(date) },
                            onEdit = { onEditDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// CalendarPanel — 表头导航 + 网格 + 事件图例 + 事件弹窗接线
// ═══════════════════════════════════════════════════════════

/**
 * 完整月历卡片（桌面信息轨与移动端日历页共用）。
 *
 * @param compact 移动端样式：周一优先 + 选中实心圆 + 「今天」胶囊 + 事件弹窗含时间。
 * @param mondayFirst 周一优先表头（独立于 [compact]，桌面信息轨也使用）。
 * @param showLegend 是否在网格下方展示当月事件日图例胶囊（对齐桌面设计稿）。
 */
@Composable
fun CalendarPanel(
    displayedYear: Int,
    displayedMonth: Int,
    selectedDate: LocalDate?,
    today: LocalDate,
    activeDates: Set<LocalDate>,
    events: List<CalendarEvent>,
    onNavigateMonth: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNavigateToToday: () -> Unit,
    onAddEvent: (String, LocalDate, Int, Boolean, Boolean, Int?) -> Unit,
    onDeleteEvent: (String) -> Unit,
    showOverlay: ((@Composable () -> Unit) -> Unit)? = null,
    hideOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    mondayFirst: Boolean = false,
    showLegend: Boolean = false,
) {
    val isCurrentMonth = displayedYear == today.year && displayedMonth == today.month.number
    val focusRequester = remember { FocusRequester() }

    // ── Dialog state (owned by CalendarPanel, not HomeScreen) ──
    var eventDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var eventDialogName by remember { mutableStateOf("") }
    var eventDialogColor by remember { mutableStateOf(0) }
    var eventDialogPinned by remember { mutableStateOf(false) }
    var eventDialogAllDay by remember { mutableStateOf(false) }
    var eventDialogTimeMinutes by remember { mutableStateOf<Int?>(null) }
    var eventDialogExistingId by remember { mutableStateOf<String?>(null) }
    // 单调递增的弹窗请求序号：即使同一天再次双击（值相等）也能重新触发效果；
    // 遮罩关闭只清了屏幕侧 dialogContent，本状态仍持有，序号变化是唯一重启信号。
    var eventDialogRequest by remember { mutableStateOf(0) }

    // 当月事件日（图例用）
    val monthEventDates = remember(events, displayedYear, displayedMonth) {
        events
            .filter { it.date.year == displayedYear && it.date.month.number == displayedMonth }
            .map { it.date }
            .distinct()
            .sortedBy { it.toEpochDays() }
            .take(6)
    }

    // Request focus on mount for keyboard nav
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // ── Keyboard navigation (inline to avoid label issues) ──
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyUp) return false
        val current = selectedDate ?: today
        return when (event.key) {
            Key.DirectionLeft -> { onSelectDate(current.plusDays(-1)); true }
            Key.DirectionRight -> { onSelectDate(current.plusDays(1)); true }
            Key.DirectionUp -> { onSelectDate(current.plusDays(-7)); true }
            Key.DirectionDown -> { onSelectDate(current.plusDays(7)); true }
            Key.T -> { onNavigateToToday(); true }
            else -> false
        }
    }

    Surface(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { handleKeyEvent(it) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onNavigateMonth(-1) }) {
                    Icon(FeatherIcons.ChevronLeft, contentDescription = "上月")
                }

                if (compact) {
                    Text(
                        text = "${displayedYear}年 ${displayedMonth}月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.CalendarIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${displayedYear}年 ${displayedMonth}月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isCurrentMonth || selectedDate != null) {
                        if (compact) {
                            // 移动端「今天」胶囊
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable { onNavigateToToday() },
                            ) {
                                Text(
                                    "今天",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            TextButton(onClick = onNavigateToToday) {
                                Text("今天", fontSize = 13.sp)
                            }
                        }
                    }
                    IconButton(onClick = { onNavigateMonth(1) }) {
                        Icon(FeatherIcons.ChevronRight, contentDescription = "下月")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            CalendarGrid(
                displayedYear = displayedYear,
                displayedMonth = displayedMonth,
                selectedDate = selectedDate,
                today = today,
                activeDates = activeDates,
                events = events,
                mondayFirst = mondayFirst || compact,
                compactSelected = compact,
                onSelectDate = onSelectDate,
                onEditDate = { date ->
                    val existing = events.find { it.date == date }
                    eventDialogDate = date
                    eventDialogName = existing?.name ?: ""
                    eventDialogColor = existing?.color ?: 0
                    eventDialogPinned = existing?.pinned ?: false
                    eventDialogAllDay = existing?.allDay ?: false
                    eventDialogTimeMinutes = existing?.timeMinutes
                    eventDialogExistingId = existing?.id
                    eventDialogRequest++  // 每次编辑请求都触发弹窗效果（同日期再次双击也可重开）
                },
            )

            // ── 事件日图例（桌面信息轨）──
            if (showLegend) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    monthEventDates.forEach { date ->
                        val color = events.firstOrNull { it.date == date }?.color ?: 0
                        val pillColor = if (color != 0) Color(color) else MaterialTheme.colorScheme.primary
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = pillColor.copy(alpha = 0.14f),
                            modifier = Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onSelectDate(date) },
                        ) {
                            Text(
                                text = date.day.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (color != 0) pillColor else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    if (monthEventDates.isNotEmpty()) {
                        Text(
                            "有事件",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
    }

    // ── 事件编辑表单 ──
    // 桌面：经 showOverlay 推送到 HomeScreen 全窗遮罩（暖色灯罩 + 居中弹窗）；
    // 移动端（compact）：直接渲染 ModalBottomSheet（自带遮罩/安全区/IME 适配）。
    if (compact) {
        val date = eventDialogDate
        if (date != null) {
            val name = eventDialogName
            val color = eventDialogColor
            val pinned = eventDialogPinned
            val allDay = eventDialogAllDay
            val timeMinutes = eventDialogTimeMinutes
            val existingId = eventDialogExistingId

            fun dismiss() {
                eventDialogDate = null
                eventDialogName = ""
                eventDialogColor = 0
                eventDialogPinned = false
                eventDialogAllDay = false
                eventDialogTimeMinutes = null
                eventDialogExistingId = null
            }

            EventEditSheet(
                date = date,
                initialName = name,
                initialColor = color,
                initialPinned = pinned,
                existingEventId = existingId,
                onSave = { n, c, p, a, t ->
                    existingId?.let { onDeleteEvent(it) }
                    onAddEvent(n, date, c, p, a, t)
                    dismiss()
                },
                onDelete = {
                    existingId?.let { onDeleteEvent(it) }
                    dismiss()
                },
                onDismiss = { dismiss() },
                initialAllDay = allDay,
                initialTimeMinutes = timeMinutes,
            )
        }
    } else {
        // ── Push dialog content to parent via showOverlay ──
        // 以请求序号为 key（而非日期值），避免「遮罩关闭后同日再次双击」因
        // 值相等而无法重启效果。
        LaunchedEffect(eventDialogRequest) {
            val date = eventDialogDate
            if (date != null && showOverlay != null) {
                // Capture values NOW — the composable lambda outlives this coroutine
                // and must not read mutable state that gets cleared on dismiss.
                val name = eventDialogName
                val color = eventDialogColor
                val pinned = eventDialogPinned
                val allDay = eventDialogAllDay
                val timeMinutes = eventDialogTimeMinutes
                val existingId = eventDialogExistingId

                fun dismiss() {
                    eventDialogDate = null
                    eventDialogName = ""
                    eventDialogColor = 0
                    eventDialogPinned = false
                    eventDialogAllDay = false
                    eventDialogTimeMinutes = null
                    eventDialogExistingId = null
                    eventDialogRequest++  // 触发效果重启 → 走 else 分支收起遮罩
                }
                showOverlay {
                    EventEditDialog(
                        date = date,
                        initialName = name,
                        initialColor = color,
                        initialPinned = pinned,
                        existingEventId = existingId,
                        onSave = { n, c, p, a, t ->
                            existingId?.let { onDeleteEvent(it) }
                            onAddEvent(n, date, c, p, a, t)
                            dismiss()
                        },
                        onDelete = {
                            existingId?.let { onDeleteEvent(it) }
                            dismiss()
                        },
                        onDismiss = { dismiss() },
                        initialAllDay = allDay,
                        initialTimeMinutes = timeMinutes,
                    )
                }
            } else {
                hideOverlay?.invoke()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// CalendarDayCell
// ═══════════════════════════════════════════════════════════

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CalendarDayCell(
    dayNum: Int,
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasActivity: Boolean,
    hasEvent: Boolean,
    eventColorInt: Int,
    compactSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val eventColor = if (eventColorInt != 0) Color(eventColorInt) else Color.Unspecified

    val bgColor = when {
        hasEvent && eventColorInt != 0 -> eventColor.copy(alpha = 0.12f)
        compactSelected && isSelected -> primaryColor
        isToday -> primaryColor.copy(alpha = 0.12f)
        isSelected -> primaryColor.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val borderMod = if (compactSelected && isSelected) {
        Modifier
    } else if (isSelected) {
        Modifier.border(2.dp, primaryColor, CircleShape)
    } else {
        Modifier
    }

    // 移动端选中日：实心主色圆 + 白字
    val dayTextColor = when {
        compactSelected && isSelected -> Color.White
        isToday -> primaryColor
        else -> onSurfaceColor
    }

    var lastTapMs by remember(date) { mutableStateOf(0L) }
    // 单调时钟基准：双击判定不受系统时间回拨（NTP/自动校时）影响
    val monoBase = remember(date) { TimeSource.Monotonic.markNow() }

    // The pointerInput coroutine below outlives recompositions; rememberUpdatedState
    // ensures it always calls the LATEST onEdit (whose closure captures the
    // currently displayed year/month), instead of the first composition's closure.
    val currentOnEdit by rememberUpdatedState(onEdit)

    Box(
        modifier = modifier
            .height(36.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(borderMod)
            .clickable {
                val now = monoBase.elapsedNow().inWholeMilliseconds
                if (now - lastTapMs < DOUBLE_TAP_WINDOW_MS) {
                    onEdit()  // double-tap → 新建（无事件）/编辑（有事件）
                } else {
                    onClick() // single tap → select
                }
                lastTapMs = now
            }
            // Right-click (desktop) → edit; uses the common pointer-input API so it
            // also compiles on Android/iOS. Touch platforms keep the double-tap path.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            currentOnEdit()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayNum.toString(),
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = dayTextColor,
                textAlign = TextAlign.Center,
            )
            // Dots
            if (hasEvent) {
                val dotColor = if (eventColorInt != 0) eventColor else primaryColor
                Box(
                    modifier = Modifier.size(5.dp).background(dotColor, CircleShape),
                )
            } else if (hasActivity) {
                Box(
                    modifier = Modifier.size(4.dp)
                        .background(primaryColor.copy(alpha = 0.5f), CircleShape),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// LocalDate helpers
// ═══════════════════════════════════════════════════════════

private fun LocalDate.plusDays(delta: Int): LocalDate {
    return LocalDate.fromEpochDays(this.toEpochDays() + delta)
}
