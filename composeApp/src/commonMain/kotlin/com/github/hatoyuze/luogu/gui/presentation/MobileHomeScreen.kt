package com.github.hatoyuze.luogu.gui.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.gui.domain.model.TodoItemDomainModel
import com.github.hatoyuze.luogu.gui.presentation.components.CalendarPanel
import com.github.hatoyuze.luogu.gui.presentation.components.DailyProblemCard
import com.github.hatoyuze.luogu.gui.presentation.components.ProblemDetailPage
import com.github.hatoyuze.luogu.gui.presentation.components.home.DashedDivider
import com.github.hatoyuze.luogu.gui.presentation.components.home.HomeContentCard
import com.github.hatoyuze.luogu.gui.presentation.components.home.InfoPill
import com.github.hatoyuze.luogu.gui.presentation.components.home.SearchInputBar
import com.github.hatoyuze.luogu.gui.presentation.components.home.StreakRow
import com.github.hatoyuze.luogu.gui.presentation.components.home.TodoInput
import com.github.hatoyuze.luogu.gui.presentation.components.home.TodoRow
import com.github.hatoyuze.luogu.gui.presentation.components.home.TopicProgressBar
import com.github.hatoyuze.luogu.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.luogu.gui.presentation.state.ChatEvent
import com.github.hatoyuze.luogu.gui.presentation.state.ChatViewModel
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel
import com.github.hatoyuze.luogu.gui.presentation.utils.formatEventTime
import com.github.hatoyuze.luogu.gui.presentation.utils.normalizeProblemId
import compose.icons.FeatherIcons
import compose.icons.feathericons.BookOpen
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Trash2
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 手机端（Compact）首页：顶部品牌栏 + 日历/今日/待办 三 Tab 分页（可横滑）。
 *
 * 与桌面 [HomeScreen]（presentation.home 包）共享全部数据层（HomeViewModel /
 * ChatViewModel）与共享原子组件（components.home：TodoInput/TodoRow/SearchInputBar/
 * InfoPill/StreakRow/TopicProgressBar 等），视觉语言双端一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    homeViewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    onOpenChat: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by homeViewModel.state.collectAsState()

    // ── Overlay state（事件编辑弹窗 / 题目详情，与桌面同构）──
    var problemDetailPid by remember { mutableStateOf<String?>(null) }
    var dialogContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var isDialogVisible by remember { mutableStateOf(false) }

    val tabTitles = listOf("日历", "今日", "待办")
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    // imePadding：键盘弹出时整体上移，保证搜索/待办输入框不被遮挡
    Box(modifier = modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏：品牌 + 设置 ──
            MobileHomeTopBar(onSettings = onSettings)

            // ── 分段胶囊 Tab + Pager ──
            MobileSegmentedTabs(
                titles = tabTitles,
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            )

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> CalendarPage(
                        state = state,
                        homeViewModel = homeViewModel,
                        showOverlay = { content -> dialogContent = content; isDialogVisible = true },
                        hideOverlay = { isDialogVisible = false; dialogContent = null },
                    )
                    1 -> TodayPage(
                        state = state,
                        onRefreshDailyProblem = { homeViewModel.refreshDailyProblem() },
                        onViewProblemDetail = { problemDetailPid = it },
                        onUpdateTopic = { name, goal -> homeViewModel.updateStudyTopic(name, goal) },
                    )
                    else -> TodoPage(
                        state = state,
                        onAddTodo = { title, dueAt -> homeViewModel.addTodo(title, dueAt) },
                        onToggleTodo = { id, completed -> homeViewModel.toggleTodo(id, completed) },
                        onDeleteTodo = { homeViewModel.deleteTodo(it) },
                    )
                }
            }
        }

        // ── 事件编辑弹窗（全屏遮罩居中，复用 EventEditDialog）──
        if (isDialogVisible) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.16f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            isDialogVisible = false; dialogContent = null
                        },
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    dialogContent?.invoke()
                }
            }
        }

        // ── 题目详情（紧凑端全屏化）──
        if (problemDetailPid != null) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.16f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            problemDetailPid = null
                        },
                )
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    ProblemDetailPage(
                        pid = problemDetailPid!!,
                        prefetchedData = state.dailyProblemState.problemDetail,
                        onBack = { problemDetailPid = null },
                        onCoachWithProblem = { pid ->
                            chatViewModel.handleEvent(ChatEvent.CreateNewSession(SessionType.COACH))
                            chatViewModel.handleEvent(ChatEvent.SendMessage("我想要学习 $pid 这道题"))
                            problemDetailPid = null
                            onOpenChat()
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 分段胶囊 Tab（替代 Material TabRow）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MobileSegmentedTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSelect(index) },
                ) {
                    Text(
                        title,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 顶栏
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MobileHomeTopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
            Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Icon(
                    FeatherIcons.BookOpen,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Shiromi",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(FeatherIcons.Settings, contentDescription = "Settings")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 日历页
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CalendarPage(
    state: HomeViewModel.HomeUiState,
    homeViewModel: HomeViewModel,
    showOverlay: (@Composable () -> Unit) -> Unit,
    hideOverlay: () -> Unit,
) {
    val selected = state.calendarViewState.selectedDate ?: state.today
    val dayEvents = state.calendarEvents.filter { it.date == selected }
        .sortedWith(compareByDescending<com.github.hatoyuze.luogu.gui.domain.model.CalendarEvent> { it.pinned }.thenBy { it.createdAtMs })

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CalendarPanel(
            displayedYear = state.calendarViewState.displayedYear,
            displayedMonth = state.calendarViewState.displayedMonth,
            selectedDate = state.calendarViewState.selectedDate,
            today = state.today,
            activeDates = state.activeDates,
            events = state.calendarEvents,
            onNavigateMonth = { homeViewModel.navigateMonth(it) },
            onSelectDate = { homeViewModel.selectDate(it) },
            onNavigateToToday = { homeViewModel.navigateToToday() },
            onAddEvent = { name, date, color, pinned, allDay, timeMinutes ->
                homeViewModel.addCalendarEvent(name, date, color, pinned, allDay, timeMinutes)
            },
            onDeleteEvent = { homeViewModel.deleteCalendarEvent(it) },
            showOverlay = showOverlay,
            hideOverlay = hideOverlay,
            modifier = Modifier.fillMaxWidth(),
            compact = true,
        )

        // ── 当日事件 ──
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selected.monthNumber}月${selected.dayOfMonth}日 · ${weekdayName(selected)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${dayEvents.size} 个事件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                if (dayEvents.isEmpty()) {
                    Text(
                        "暂无事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    dayEvents.forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (event.color != 0) Color(event.color) else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(10.dp, 10.dp),
                            ) {}
                            Spacer(Modifier.width(10.dp))
                            Text(
                                event.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // 时间列（全天 / HH:mm / 未指定为空）
                            val eventTimeText = formatEventTime(event.allDay, event.timeMinutes)
                            if (eventTimeText.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    eventTimeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            if (event.pinned) {
                                Text("置顶", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.width(6.dp))
                            }
                            IconButton(
                                onClick = { homeViewModel.deleteCalendarEvent(event.id) },
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    FeatherIcons.Trash2,
                                    contentDescription = "删除事件",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
                Text(
                    "双击日期新建事件 · 点按选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun weekdayName(date: LocalDate): String {
    val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    // 1970-01-01 是周四：以周一为 0 的星期序号 = (epochDays + 3) % 7
    val mondayBasedIndex = ((date.toEpochDays() + 3) % 7).toInt()
    return names[mondayBasedIndex]
}

private fun monthNameCn(month: Int): String = listOf(
    "一月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "十一月", "十二月",
)[month - 1]

// ═══════════════════════════════════════════════════════════════
// 今日页
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TodayPage(
    state: HomeViewModel.HomeUiState,
    onRefreshDailyProblem: () -> Unit,
    onViewProblemDetail: (String) -> Unit,
    onUpdateTopic: (String, Int) -> Unit,
) {
    val today = state.today
    val todayEventCount = state.calendarEvents.count { it.date == today }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 日期 hero ──
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${today.dayOfMonth}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.padding(bottom = 5.dp)) {
                        Text(
                            "${monthNameCn(today.monthNumber)} · ${weekdayName(today)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${today.year}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        state.randomKaomoji,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill("连续 ${state.streakDays} 天", warm = true, icon = AppIcons.RiseFilling)
                    if (todayEventCount > 0) {
                        InfoPill("$todayEventCount 个事件", warm = false)
                    }
                }
            }
        }

        // ── 每日推荐（共享卡片）──
        DailyProblemCard(
            state = state.dailyProblemState,
            onRefresh = onRefreshDailyProblem,
            onViewDetail = onViewProblemDetail,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── 搜索（共享输入条：尾部圆形发送钮，不依赖 Enter 键）──
        var searchQuery by remember { mutableStateOf("") }
        var searchError by remember { mutableStateOf(false) }
        SearchInputBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it; searchError = false },
            onSubmit = {
                val pid = normalizeProblemId(searchQuery)
                if (pid == null) {
                    searchError = true
                } else {
                    searchError = false
                    onViewProblemDetail(pid)
                }
            },
            isError = searchError,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── 学习进度 ──
        HomeContentCard(title = "学习进度", icon = AppIcons.ChartBar, modifier = Modifier.fillMaxWidth()) {
            // 解题统计暂无写入链路时（solvedTotal=0）隐藏，避免误导
            if (state.solvedTotal > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "已刷题 ${state.solvedTotal} · 本周 +${state.solvedThisWeek}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "目标 ${state.studyTopic.goalCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            StreakRow(streakDays = state.streakDays)
            Spacer(Modifier.height(10.dp))
            TopicProgressBar(topic = state.studyTopic, onUpdateTopic = onUpdateTopic)
        }

        // ── 鼓励语 ──
        Text(
            text = state.encouragementText,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 待办页（共享 TodoInput/TodoRow/DashedDivider + 移动端本周概览卡）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TodoPage(
    state: HomeViewModel.HomeUiState,
    onAddTodo: (String, Long?) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onDeleteTodo: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MobileTodoCard(
            todos = state.todos,
            onAddTodo = onAddTodo,
            onToggleTodo = onToggleTodo,
            onDeleteTodo = onDeleteTodo,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── 本周概览（完成率）──
        if (state.todos.isNotEmpty()) {
            val completed = state.todos.count { it.completed }
            MobileOverviewCard(
                completed = completed,
                total = state.todos.size,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            "勾选完成 · 右侧删除",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun MobileTodoCard(
    todos: List<TodoItemDomainModel>,
    onAddTodo: (String, Long?) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onDeleteTodo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            TodoInput(onAdd = onAddTodo)
            Spacer(Modifier.height(12.dp))
            if (todos.isEmpty()) {
                Text(
                    "暂无待办事项",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                todos.forEachIndexed { index, todo ->
                    if (index > 0) DashedDivider()
                    TodoRow(
                        todo = todo,
                        onToggle = { onToggleTodo(todo.id, todo.completed) },
                        onDelete = { onDeleteTodo(todo.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileOverviewCard(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("本周概览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "待办完成率 $completed / $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { completed.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
