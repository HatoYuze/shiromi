package com.github.hatoyuze.luogu.gui.presentation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.gui.presentation.components.CalendarPanel
import com.github.hatoyuze.luogu.gui.presentation.components.DailyProblemCard
import com.github.hatoyuze.luogu.gui.presentation.components.ProblemDetailPage
import com.github.hatoyuze.luogu.gui.presentation.state.ChatEvent
import com.github.hatoyuze.luogu.gui.presentation.state.ChatViewModel
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.BookOpen
import compose.icons.feathericons.Send
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.Search
import compose.icons.feathericons.Settings
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 手机端（Compact）首页：顶部品牌栏 + 日历/今日/待办 三 Tab 分页（可横滑）。
 *
 * 与桌面 [HomeScreen] 共享全部数据层（HomeViewModel / ChatViewModel）与
 * 卡片组件；桌面三区布局在 Expanded 下原样保留。
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏：品牌 + 设置 ──
            MobileHomeTopBar(onSettings = onSettings)

            // ── TabRow + Pager ──
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title, fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }

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
                        onAddTodo = { homeViewModel.addTodo(it) },
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
            onAddEvent = { name, date, color, pinned -> homeViewModel.addCalendarEvent(name, date, color, pinned) },
            onDeleteEvent = { homeViewModel.deleteCalendarEvent(it) },
            showOverlay = showOverlay,
            hideOverlay = hideOverlay,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── 当日事件 ──
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
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
                        "双击日期可新建事件",
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
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
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
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
                            "${today.monthNumber}月 · ${weekdayName(today)}",
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
                    AssistChip(onClick = {}, label = { Text("🔥 连续 ${state.streakDays} 天") })
                    if (todayEventCount > 0) {
                        AssistChip(onClick = {}, label = { Text("$todayEventCount 个事件") })
                    }
                }
            }
        }

        // ── 每日推荐 ──
        DailyProblemCard(
            state = state.dailyProblemState,
            onRefresh = onRefreshDailyProblem,
            onViewDetail = onViewProblemDetail,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── 搜索（尾部按钮提交，不依赖 Enter 键）──
        var searchQuery by remember { mutableStateOf("") }
        var searchError by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; searchError = false },
            placeholder = { Text("搜索题目编号…", fontSize = 14.sp) },
            leadingIcon = {
                Icon(FeatherIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        val pid = searchQuery.trim()
                        val valid = pid.startsWith("P", ignoreCase = true) || pid.all { it.isDigit() }
                        if (pid.isBlank() || !valid) {
                            searchError = true
                        } else {
                            searchError = false
                            onViewProblemDetail(pid)
                        }
                    },
                    enabled = searchQuery.isNotBlank(),
                ) {
                    Icon(
                        FeatherIcons.Send, contentDescription = "搜索",
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            singleLine = true,
            isError = searchError,
            supportingText = if (searchError) {
                { Text("未找到该题目，请检查编号", fontSize = 12.sp) }
            } else null,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        // ── 学习进度 ──
        ContentCard(title = "📊 学习进度", modifier = Modifier.fillMaxWidth()) {
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
// 待办页
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TodoPage(
    state: HomeViewModel.HomeUiState,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onDeleteTodo: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContentCard(title = "📋 待办", modifier = Modifier.fillMaxWidth()) {
            TodoInput(onAdd = onAddTodo)
            Spacer(Modifier.height(8.dp))
            if (state.todos.isEmpty()) {
                Text(
                    "暂无待办事项",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                state.todos.forEach { todo ->
                    TodoRow(
                        todo = todo,
                        onToggle = { onToggleTodo(todo.id, todo.completed) },
                        onDelete = { onDeleteTodo(todo.id) },
                    )
                }
            }
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
