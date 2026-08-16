package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronLeft
import compose.icons.feathericons.ChevronRight
import kotlin.time.TimeSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

// ═══════════════════════════════════════════════════════════════
// Calendar helpers
// ═══════════════════════════════════════════════════════════════

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

private val WEEKDAY_HEADERS = listOf("日", "一", "二", "三", "四", "五", "六")

/** 双击判定窗口：两次点击间隔小于该值视为双击（新建/编辑事件）。 */
private const val DOUBLE_TAP_WINDOW_MS = 400L

// ═══════════════════════════════════════════════════════════════
// CalendarPanel
// ═══════════════════════════════════════════════════════════════

@Composable
fun CalendarPanel(
    displayedYear: Int,
    displayedMonth: Int,
    selectedDate: LocalDate?,
    today: LocalDate,
    activeDates: Set<LocalDate>,
    events: List<com.github.hatoyuze.luogu.gui.domain.model.CalendarEvent>,
    onNavigateMonth: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNavigateToToday: () -> Unit,
    onAddEvent: (String, LocalDate, Int, Boolean, Boolean, Int?) -> Unit,
    onDeleteEvent: (String) -> Unit,
    showOverlay: ((@Composable () -> Unit) -> Unit)? = null,
    hideOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val isCurrentMonth = displayedYear == today.year && displayedMonth == today.month.number
    val days = daysInMonth(displayedYear, displayedMonth)
    val offset = firstDayOffset(displayedYear, displayedMonth, mondayFirst = compact)
    val focusRequester = remember { FocusRequester() }
    // 移动端（compact）周一优先：一、二、三、四、五、六、日
    val weekdayHeaders = if (compact) listOf("一", "二", "三", "四", "五", "六", "日") else WEEKDAY_HEADERS

    // ── Dialog state (owned by CalendarPanel, not HomeScreen) ──
    var eventDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var eventDialogName by remember { mutableStateOf("") }
    var eventDialogColor by remember { mutableStateOf(0) }
    var eventDialogPinned by remember { mutableStateOf(false) }
    var eventDialogAllDay by remember { mutableStateOf(false) }
    var eventDialogTimeMinutes by remember { mutableStateOf<Int?>(null) }
    var eventDialogExistingId by remember { mutableStateOf<String?>(null) }

    // Build lookup maps from events list
    val eventDates = remember(events) { events.map { it.date }.toSet() }
    val eventColorMap = remember(events) { events.associate { it.date to it.color } }

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
            border = BorderStroke(
                1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            ),
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

                Text(
                    text = "${displayedYear}年 ${displayedMonth}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

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
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                hasActivity = date in activeDates,
                                hasEvent = date in eventDates,
                                eventColorInt = eventColorMap[date] ?: 0,
                                onClick = { onSelectDate(date) },
                                onRightClick = {
                                    val existing = events.find { it.date == date }
                                    eventDialogDate = date
                                    eventDialogName = existing?.name ?: ""
                                    eventDialogColor = existing?.color ?: 0
                                    eventDialogPinned = existing?.pinned ?: false
                                    eventDialogAllDay = existing?.allDay ?: false
                                    eventDialogTimeMinutes = existing?.timeMinutes
                                    eventDialogExistingId = existing?.id
                                },
                                modifier = Modifier.weight(1f),
                                compact = compact,
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // ── Push dialog content to parent via showOverlay ──
    LaunchedEffect(eventDialogDate) {
        if (eventDialogDate != null && showOverlay != null) {
            // Capture values NOW — the composable lambda outlives this coroutine
            // and must not read mutable state that gets cleared on dismiss.
            val date = eventDialogDate!!
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
                    showTime = compact,
                    initialAllDay = allDay,
                    initialTimeMinutes = timeMinutes,
                )
            }
        } else {
            hideOverlay?.invoke()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CalendarDayCell
// ═══════════════════════════════════════════════════════════════

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CalendarDayCell(
    dayNum: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasActivity: Boolean,
    hasEvent: Boolean,
    eventColorInt: Int,
    onClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val eventColor = if (eventColorInt != 0) Color(eventColorInt) else Color.Unspecified

    val bgColor = when {
        hasEvent && eventColorInt != 0 -> eventColor.copy(alpha = 0.12f)
        compact && isSelected -> primaryColor
        isToday -> primaryColor.copy(alpha = 0.12f)
        isSelected -> primaryColor.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val borderMod = if (compact && isSelected) {
        Modifier
    } else if (isSelected) {
        Modifier.border(2.dp, primaryColor, CircleShape)
    } else {
        Modifier
    }

    // 移动端选中日：实心主色圆 + 白字
    val dayTextColor = when {
        compact && isSelected -> Color.White
        isToday -> primaryColor
        else -> onSurfaceColor
    }

    var lastTapMs by remember { mutableStateOf(0L) }
    // 单调时钟基准：双击判定不受系统时间回拨（NTP/自动校时）影响
    val monoBase = remember { TimeSource.Monotonic.markNow() }

    // The pointerInput coroutine below outlives recompositions; rememberUpdatedState
    // ensures it always calls the LATEST onRightClick (whose closure captures the
    // currently displayed year/month), instead of the first composition's closure.
    val currentOnRightClick by rememberUpdatedState(onRightClick)

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
                    onRightClick()  // double-tap → 新建（无事件）/编辑（有事件）
                } else {
                    onClick()       // single tap → select
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
                            currentOnRightClick()
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

// ═══════════════════════════════════════════════════════════════
// LocalDate helpers
// ═══════════════════════════════════════════════════════════════

private fun LocalDate.plusDays(delta: Int): LocalDate {
    return LocalDate.fromEpochDays(this.toEpochDays() + delta)
}
