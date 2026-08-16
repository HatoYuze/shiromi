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
import com.github.hatoyuze.luogu.gui.platform.currentTimeMillis
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronLeft
import compose.icons.feathericons.ChevronRight
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

fun firstDayOffset(year: Int, month: Int): Int {
    val firstDay = LocalDate(year, month, 1)
    return (firstDay.dayOfWeek.ordinal + 1) % 7
}

private val WEEKDAY_HEADERS = listOf("日", "一", "二", "三", "四", "五", "六")

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
    onAddEvent: (String, LocalDate, Int, Boolean) -> Unit,
    onDeleteEvent: (String) -> Unit,
    showOverlay: ((@Composable () -> Unit) -> Unit)? = null,
    hideOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isCurrentMonth = displayedYear == today.year && displayedMonth == today.month.number
    val days = daysInMonth(displayedYear, displayedMonth)
    val offset = firstDayOffset(displayedYear, displayedMonth)
    val focusRequester = remember { FocusRequester() }

    // ── Dialog state (owned by CalendarPanel, not HomeScreen) ──
    var eventDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var eventDialogName by remember { mutableStateOf("") }
    var eventDialogColor by remember { mutableStateOf(0) }
    var eventDialogPinned by remember { mutableStateOf(false) }
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
                        TextButton(onClick = onNavigateToToday) {
                            Text("今天", fontSize = 13.sp)
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
                WEEKDAY_HEADERS.forEach { day ->
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
                                    eventDialogExistingId = existing?.id
                                },
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

    // ── Push dialog content to parent via showOverlay ──
    LaunchedEffect(eventDialogDate) {
        if (eventDialogDate != null && showOverlay != null) {
            // Capture values NOW — the composable lambda outlives this coroutine
            // and must not read mutable state that gets cleared on dismiss.
            val date = eventDialogDate!!
            val name = eventDialogName
            val color = eventDialogColor
            val pinned = eventDialogPinned
            val existingId = eventDialogExistingId

            fun dismiss() {
                eventDialogDate = null
                eventDialogName = ""
                eventDialogColor = 0
                eventDialogPinned = false
                eventDialogExistingId = null
            }
            showOverlay {
                EventEditDialog(
                    date = date,
                    initialName = name,
                    initialColor = color,
                    initialPinned = pinned,
                    existingEventId = existingId,
                    onSave = { n, c, p ->
                        existingId?.let { onDeleteEvent(it) }
                        onAddEvent(n, date, c, p)
                        dismiss()
                    },
                    onDelete = {
                        existingId?.let { onDeleteEvent(it) }
                        dismiss()
                    },
                    onDismiss = { dismiss() },
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
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val eventColor = if (eventColorInt != 0) Color(eventColorInt) else Color.Unspecified

    val bgColor = when {
        hasEvent && eventColorInt != 0 -> eventColor.copy(alpha = 0.12f)
        isToday -> primaryColor.copy(alpha = 0.12f)
        isSelected -> primaryColor.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val borderMod = if (isSelected) {
        Modifier.border(2.dp, primaryColor, CircleShape)
    } else {
        Modifier
    }

    var lastTapMs by remember { mutableStateOf(0L) }

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
                val now = currentTimeMillis()
                if (hasEvent && now - lastTapMs < 400L) {
                    onRightClick()  // double-tap → edit
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
                color = when {
                    isToday -> primaryColor
                    else -> onSurfaceColor
                },
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
