// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.hatoyuze.luogu.gui.presentation.state

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.hatoyuze.luogu.gui.data.remote.DailyProblemAgent
import com.github.hatoyuze.luogu.gui.domain.interfaces.ChatRepository
import com.github.hatoyuze.luogu.gui.domain.model.*
import com.github.hatoyuze.luogu.gui.platform.currentTimeMillis
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.time.Instant

// ═══════════════════════════════════════════════════════════
// State data classes
// ═══════════════════════════════════════════════════════════

@Immutable
data class CalendarViewState(
    val displayedYear: Int,
    val displayedMonth: Int,
    val selectedDate: LocalDate? = null,
)

@Immutable
data class SelectedDateItems(
    val date: LocalDate,
    val sessions: List<ChatSessionDomainModel>,
    val todos: List<TodoItemDomainModel>,
)

// ═══════════════════════════════════════════════════════════
// Kaomoji & encouragement
// ═══════════════════════════════════════════════════════════

private val KAOMOJIS = listOf(
    "( ˘ ³˘)♥", "(◕‿◕✿)", "✧(•̀ᴗ•́)و", "(๑˃̵ᴗ˂̵)و",
    "٩(◕‿◕｡)۶", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "╰(✿´⌣`✿)╯",
    "(≧◡≦)", "o(>ω<)o", "(｡•̀ᴗ-)✧",
)

private val ENCOURAGEMENTS = listOf(
    "今天也要加油哦！", "坚持就是胜利~", "每天进步一点点 ✨",
    "刷题使我快乐！", "算法之美，尽在代码中", "AC 的路上不孤单",
    "好记性不如烂笔头", "静下心来，专注当下", "学如逆水行舟",
)

/** 一周毫秒数：用于「本周 +N」解题统计（滚动 7 天）。 */
private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

// ═══════════════════════════════════════════════════════════
// HomeViewModel
// ═══════════════════════════════════════════════════════════

class HomeViewModel(
    private val repository: ChatRepository,
    private val dailyProblemAgent: DailyProblemAgent,
) : ViewModel() {

    @Immutable
    data class HomeUiState(
        // Existing
        val sessions: List<ChatSessionDomainModel> = emptyList(),
        val todos: List<TodoItemDomainModel> = emptyList(),
        val recommendations: List<String> = emptyList(),

        // Date & calendar (today computed in ViewModel init to avoid NoClassDefFoundError)
        val today: LocalDate = LocalDate(2026, 1, 1),  // placeholder, set in init
        val calendarViewState: CalendarViewState = CalendarViewState(
            displayedYear = 2026, displayedMonth = 1
        ),
        val selectedDateItems: SelectedDateItems? = null,
        val activeDates: Set<LocalDate> = emptySet(),

        // Learning progress
        val streakDays: Int = 0,
        val studyTopic: StudyTopic = StudyTopic(),
        val solvedTotal: Long = 0,      // 累计已解题数（CompletedProblem）
        val solvedThisWeek: Long = 0,   // 近 7 天已解题数

        // Calendar events (non-expired only, pinned first)
        val calendarEvents: List<CalendarEvent> = emptyList(),

        // Daily problem
        val dailyProblemState: DailyProblemAgent.DailyProblemState = DailyProblemAgent.DailyProblemState(),

        // Center display
        val randomKaomoji: String = KAOMOJIS.random(),
        val encouragementText: String = ENCOURAGEMENTS.random(),
    )

    private val _state = MutableStateFlow(
        HomeUiState().copy(
            today = today,
            calendarViewState = CalendarViewState(
                displayedYear = today.year,
                displayedMonth = today.month.number,
            ),
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Computed lazily — avoid calling kotlinx-datetime during class init. */
    private val today: LocalDate
        get() {
            val now = kotlin.time.Clock.System.now()
            return now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        }

    private val todayKey: String
        get() = today.toString()  // "2026-07-08" — changes daily

    init {
        // Trigger async agent initialization (non-blocking)
        dailyProblemAgent.initialize()

        // Core state (sessions, todos, recommendations, study topic)
        viewModelScope.launch {
            combine(
                repository.getAllSessions(),
                repository.getAllTodos(),
                repository.getAllRecommendations(),
                repository.getStudyTopic(),
            ) { sessions, todos, recs, topic ->
                val prevState = _state.value
                val currentToday = today
                val dailyKey = currentToday.toString()

                HomeUiState(
                    sessions = sessions,
                    todos = todos,
                    recommendations = recs,
                    today = currentToday,
                    activeDates = computeActiveDates(sessions, todos, prevState.calendarEvents),
                    streakDays = computeStreak(sessions, todos, currentToday),
                    studyTopic = topic,
                    solvedTotal = prevState.solvedTotal,
                    solvedThisWeek = prevState.solvedThisWeek,
                    calendarEvents = prevState.calendarEvents,
                    calendarViewState = prevState.calendarViewState,
                    selectedDateItems = prevState.selectedDateItems,
                    dailyProblemState = prevState.dailyProblemState,
                    randomKaomoji = if (dailyKey != todayKey) KAOMOJIS.random() else prevState.randomKaomoji,
                    encouragementText = if (dailyKey != todayKey) ENCOURAGEMENTS.random() else prevState.encouragementText,
                )
            }.distinctUntilChanged().collect { _state.value = it }
        }

        // 解题统计（独立收集器）：以 CompletedProblem 表变化为触发（防抖重算），
        // DB 异常降级为 0，不阻塞也不杀死主 state 流
        viewModelScope.launch {
            repository.getCompletedProblemChanges()
                .onStart { emit(Unit) }
                .debounce(500)
                .collect {
                    val now = com.github.hatoyuze.luogu.gui.platform.currentTimeMillis()
                    val total = runCatching { repository.countAllCompletedProblems() }.getOrDefault(0L)
                    val week = runCatching {
                        repository.countCompletedProblemsSince(now - WEEK_MILLIS)
                    }.getOrDefault(0L)
                    _state.update { it.copy(solvedTotal = total, solvedThisWeek = week) }
                }
        }

        // Active calendar events: non-expired only, re-queries when today changes
        viewModelScope.launch {
            _state
                .map { it.today.toEpochDays() }
                .distinctUntilChanged()
                .flatMapLatest { todayDays ->
                    repository.getActiveEvents(todayDays)
                }
                .collect { events ->
                    _state.update { it.copy(calendarEvents = events) }
                }
        }

        // Daily problem agent state
        viewModelScope.launch {
            dailyProblemAgent.state.collect { dpState ->
                _state.update { it.copy(dailyProblemState = dpState) }
            }
        }

        // Day change detection → auto-refresh daily problem
        viewModelScope.launch {
            _state
                .map { it.today.toString() }
                .distinctUntilChanged()
                .collect { dailyProblemAgent.checkDayChange() }
        }
    }

    // ══════════════════════════════════════════════════════
    // Calendar actions
    // ══════════════════════════════════════════════════════

    fun navigateMonth(direction: Int) {
        _state.update { state ->
            var (year, month) = state.calendarViewState.displayedYear to state.calendarViewState.displayedMonth
            month += direction
            if (month < 1) { month = 12; year-- }
            if (month > 12) { month = 1; year++ }
            state.copy(
                calendarViewState = state.calendarViewState.copy(
                    displayedYear = year,
                    displayedMonth = month,
                ),
            )
        }
    }

    fun selectDate(date: LocalDate?) {
        _state.update { state ->
            val items = if (date != null) {
                val sessionsOnDate = state.sessions.filter {
                    it.createdAt.toLocalDate() == date || it.lastModified.toLocalDate() == date
                }
                val todosOnDate = state.todos.filter {
                    it.createdAt.toLocalDate() == date
                }
                if (sessionsOnDate.isNotEmpty() || todosOnDate.isNotEmpty()) {
                    SelectedDateItems(date, sessionsOnDate, todosOnDate)
                } else null
            } else null
            // Auto-navigate displayed month if selected date is outside current view
            val newDisplayedYear = date?.year ?: state.calendarViewState.displayedYear
            val newDisplayedMonth = date?.month?.number ?: state.calendarViewState.displayedMonth
            state.copy(
                calendarViewState = state.calendarViewState.copy(
                    displayedYear = newDisplayedYear,
                    displayedMonth = newDisplayedMonth,
                    selectedDate = date,
                ),
                selectedDateItems = items,
            )
        }
    }

    fun navigateToToday() {
        _state.update { state ->
            state.copy(
                calendarViewState = CalendarViewState(
                    displayedYear = today.year,
                    displayedMonth = today.month.number,
                    selectedDate = null,
                ),
                selectedDateItems = null,
            )
        }
    }

    // ══════════════════════════════════════════════════════
    // Calendar event actions
    // ══════════════════════════════════════════════════════

    fun addCalendarEvent(
        name: String,
        date: LocalDate,
        color: Int = 0,
        pinned: Boolean = false,
        allDay: Boolean = false,
        timeMinutes: Int? = null,
    ) {
        viewModelScope.launch {
            repository.insertCalendarEvent(
                CalendarEvent(
                    id = "${currentTimeMillis()}",
                    name = name,
                    date = date,
                    createdAtMs = currentTimeMillis(),
                    color = color,
                    pinned = pinned,
                    allDay = allDay,
                    timeMinutes = timeMinutes,
                )
            )
        }
    }

    fun deleteCalendarEvent(eventId: String) {
        viewModelScope.launch {
            repository.deleteCalendarEvent(eventId)
        }
    }

    fun toggleCalendarEventPin(eventId: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.updateCalendarEventPin(eventId, pinned)
        }
    }

    // ══════════════════════════════════════════════════════
    // Daily problem actions
    // ══════════════════════════════════════════════════════

    fun refreshDailyProblem() {
        dailyProblemAgent.refresh()
    }

    // ══════════════════════════════════════════════════════
    // Study topic actions (persisted)
    // ══════════════════════════════════════════════════════

    fun updateStudyTopic(name: String, goalCount: Int) {
        val topic = StudyTopic(name = name, goalCount = goalCount, currentCount = 0)
        viewModelScope.launch { repository.saveStudyTopic(topic) }
    }

    fun incrementTopicProgress() {
        val topic = _state.value.studyTopic
        val updated = topic.copy(currentCount = (topic.currentCount + 1).coerceAtMost(topic.goalCount))
        viewModelScope.launch { repository.saveStudyTopic(updated) }
    }

    fun resetTopicProgress() {
        val topic = _state.value.studyTopic.copy(currentCount = 0)
        viewModelScope.launch { repository.saveStudyTopic(topic) }
    }

    // ══════════════════════════════════════════════════════
    // Todo actions (existing)
    // ══════════════════════════════════════════════════════

    fun addTodo(title: String, dueAt: Long? = null) {
        viewModelScope.launch {
            val todo = TodoItemDomainModel(
                id = "${currentTimeMillis()}",
                title = title,
                completed = false,
                createdAt = currentTimeMillis(),
                dueAt = dueAt,
            )
            repository.insertTodo(todo)
        }
    }

    fun toggleTodo(id: String, completed: Boolean) {
        viewModelScope.launch {
            repository.updateTodoCompleted(id, !completed)
        }
    }

    fun deleteTodo(id: String) {
        viewModelScope.launch {
            repository.deleteTodo(id)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    // ══════════════════════════════════════════════════════
    // Derived computations
    // ══════════════════════════════════════════════════════

    private fun computeActiveDates(
        sessions: List<ChatSessionDomainModel>,
        todos: List<TodoItemDomainModel>,
        events: List<CalendarEvent>,
    ): Set<LocalDate> = buildSet {
        sessions.forEach {
            add(it.createdAt.toLocalDate())
            add(it.lastModified.toLocalDate())
        }
        todos.forEach { add(it.createdAt.toLocalDate()) }
        // events tracked independently via eventDates/eventColorMap in CalendarPanel
    }

    /** Streak: consecutive days with at least one session or todo. */
    private fun computeStreak(
        sessions: List<ChatSessionDomainModel>,
        todos: List<TodoItemDomainModel>,
        today: LocalDate,
    ): Int {
        val activeDays = buildSet {
            sessions.forEach { add(it.createdAt.toLocalDate()) }
            todos.forEach { add(it.createdAt.toLocalDate()) }
        }
        var streak = 0
        var date = today
        while (date in activeDays) {
            streak++
            date = date.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }
}

// ═══════════════════════════════════════════════════════════════
// Utility: Long epoch millis → LocalDate
// ═══════════════════════════════════════════════════════════════

private fun Long.toLocalDate(): LocalDate {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
}
