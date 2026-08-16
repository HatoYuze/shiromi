package com.github.hatoyuze.luogu.gui.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.*
import com.github.hatoyuze.luogu.gui.presentation.components.CalendarPanel
import com.github.hatoyuze.luogu.gui.presentation.components.HomeDesignTokens
import com.github.hatoyuze.luogu.gui.presentation.components.ExtensionSlot
import com.github.hatoyuze.luogu.gui.presentation.components.DailyProblemCard
import com.github.hatoyuze.luogu.gui.presentation.components.DateDisplayBlock
import com.github.hatoyuze.luogu.gui.presentation.components.ProblemDetailPage
import com.github.hatoyuze.luogu.gui.presentation.state.ChatEvent
import com.github.hatoyuze.luogu.gui.presentation.state.ChatViewModel
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel
import com.github.hatoyuze.luogu.gui.theme.LocalThemeIsDark
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

// ═══════════════════════════════════════════════════════════════
// HomeScreen
// ═══════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by homeViewModel.state.collectAsState()
    val isDark by LocalThemeIsDark.current

    // ── Overlay state (lifted to cover full window including sidebar) ──
    var problemDetailPid by remember { mutableStateOf<String?>(null) }
    var dialogContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var isDialogVisible by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            HomeSidebar(
                sessions = state.sessions,
                recommendations = state.recommendations,
                onNewChat = { chatViewModel.handleEvent(ChatEvent.CreateNewSession(SessionType.CHAT)) },
                onNewCoach = { chatViewModel.handleEvent(ChatEvent.CreateNewSession(SessionType.COACH)) },
                onSelectSession = { chatViewModel.handleEvent(ChatEvent.SelectSession(it)) },
                onDeleteSession = { homeViewModel.deleteSession(it) },
                onSettings = onSettings,
                modifier = Modifier.width(260.dp).fillMaxHeight(),
            )

            HorizontalDivider(
                modifier = Modifier.fillMaxHeight().width(1.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            )

            HomeMainContent(
                state = state,
                isDark = isDark,
                onNavigateMonth = { homeViewModel.navigateMonth(it) },
                onSelectDate = { homeViewModel.selectDate(it) },
                onNavigateToToday = { homeViewModel.navigateToToday() },
                onAddTodo = { homeViewModel.addTodo(it) },
                onToggleTodo = { id, completed -> homeViewModel.toggleTodo(id, completed) },
                onDeleteTodo = { homeViewModel.deleteTodo(it) },
                onAddEvent = { name, date, color, pinned -> homeViewModel.addCalendarEvent(name, date, color, pinned) },
                onDeleteEvent = { homeViewModel.deleteCalendarEvent(it) },
                onUpdateTopic = { name, goal -> homeViewModel.updateStudyTopic(name, goal) },
                onRefreshDailyProblem = { homeViewModel.refreshDailyProblem() },
                onViewProblemDetail = { pid -> problemDetailPid = pid },
                showOverlay = { content -> dialogContent = content; isDialogVisible = true },
                hideOverlay = { isDialogVisible = false; dialogContent = null },
                onCoachWithProblem = { pid ->
                    chatViewModel.handleEvent(ChatEvent.CreateNewSession(SessionType.COACH))
                    chatViewModel.handleEvent(ChatEvent.SendMessage("我想要学习 $pid 这道题"))
                },
                modifier = Modifier.fillMaxHeight().weight(1f),
            )
        }

        // ── EventEdit overlay (covers full window) ──
        if (isDialogVisible) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.16f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() })
                    { isDialogVisible = false; dialogContent = null })
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    dialogContent?.invoke()
                }
            }
        }

        // ── Problem detail overlay (covers full window) ──
        if (problemDetailPid != null) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.16f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() })
                    { problemDetailPid = null })
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    ProblemDetailPage(
                        pid = problemDetailPid!!,
                        prefetchedData = state.dailyProblemState.problemDetail,
                        onBack = { problemDetailPid = null },
                        onCoachWithProblem = { pid ->
                            chatViewModel.handleEvent(ChatEvent.CreateNewSession(SessionType.COACH))
                            chatViewModel.handleEvent(ChatEvent.SendMessage("我想要学习 $pid 这道题"))
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HomeSidebar
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HomeSidebar(
    sessions: List<ChatSessionDomainModel>,
    recommendations: List<String>,
    onNewChat: () -> Unit,
    onNewCoach: () -> Unit,
    onSelectSession: (ChatSessionDomainModel) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRecommendations by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Brand ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            FeatherIcons.BookOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "LuoguHelper",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── New Chat ──
            SidebarActionButton(
                icon = FeatherIcons.Plus,
                label = "New Chat",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onNewChat,
            )

            Spacer(Modifier.height(8.dp))

            // ── New Coach ──
            SidebarActionButton(
                icon = FeatherIcons.Cpu,
                label = "New Coach",
                tint = MaterialTheme.colorScheme.secondary,
                onClick = onNewCoach,
            )

            Spacer(Modifier.height(16.dp))

            // ── Settings ──
            TextButton(
                onClick = onSettings,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    FeatherIcons.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Settings",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))

            // ── History label ──
            Text(
                "History",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(8.dp))

            // ── Session list ──
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions, key = { it.id }) { session ->
                    HomeSessionItem(
                        session = session,
                        onClick = { onSelectSession(session) },
                        onDelete = { onDeleteSession(session.id) },
                    )
                }
                if (sessions.isEmpty()) {
                    item {
                        Text(
                            "No sessions yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }

            // ── Recommendations (collapsible, at bottom) ──
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRecommendations = !showRecommendations }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "💡 推荐",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (showRecommendations) "▾" else "▸",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }

            if (showRecommendations) {
                if (recommendations.isEmpty()) {
                    Text(
                        "教练推荐将显示在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else {
                    recommendations.take(3).forEach { rec ->
                        Text(
                            text = rec,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SidebarActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.2f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = tint)
        }
    }
}

@Composable
internal fun HomeSessionItem(
    session: ChatSessionDomainModel,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .also {
                    // Note: Compose Desktop pointerMoveFilter is not available in commonMain
                    // Hover will be handled by Material ripple on clickable
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (session.type == SessionType.COACH) FeatherIcons.BookOpen else FeatherIcons.MessageSquare,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (session.type == SessionType.COACH)
                    MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isHovered) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        FeatherIcons.Trash2,
                        contentDescription = "Delete",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HomeMainContent
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HomeMainContent(
    state: HomeViewModel.HomeUiState,
    isDark: Boolean,
    onNavigateMonth: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNavigateToToday: () -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onAddEvent: (String, LocalDate, Int, Boolean) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onUpdateTopic: (String, Int) -> Unit,
    onRefreshDailyProblem: () -> Unit,
    onViewProblemDetail: (String) -> Unit,
    showOverlay: (@Composable () -> Unit) -> Unit,
    hideOverlay: () -> Unit,
    onCoachWithProblem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        // ── Main area: left scrollable + right panel ──
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // ── Left: scrollable content (constrained to max 800dp) ──
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(start = 48.dp)
                    .widthIn(max = HomeDesignTokens.ContentMaxWidth),
            ) {
                Spacer(Modifier.height(32.dp))

                // ── 1. Calendar + DateDisplay (matching heights) ──
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
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
                        modifier = Modifier.weight(0.52f)
                            .widthIn(max = HomeDesignTokens.CalendarMaxWidth).fillMaxHeight(),
                    )

                    Spacer(Modifier.width(HomeDesignTokens.RowSpacing))

                    DateDisplayBlock(
                        displayDate = state.calendarViewState.selectedDate ?: state.today,
                        isToday = state.calendarViewState.selectedDate == null
                                || state.calendarViewState.selectedDate == state.today,
                        events = state.calendarEvents,
                        modifier = Modifier.weight(0.48f)
                            .widthIn(max = HomeDesignTokens.DateDisplayMaxWidth).fillMaxHeight(),
                    )
                }

                Spacer(Modifier.height(HomeDesignTokens.SectionSpacing))

                // ── 2. Search Bar ──
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; searchError = false },
                    placeholder = { Text("输入题目编号搜索...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(FeatherIcons.Search, contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    },
                    singleLine = true, isError = searchError,
                    supportingText = if (searchError) {
                        { Text("未找到该题目，请检查编号", fontSize = 12.sp) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = if (searchError)
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    else OutlinedTextFieldDefaults.colors(),
                    modifier = Modifier.fillMaxWidth()
                        .widthIn(max = HomeDesignTokens.SearchBarMaxWidth)
                        .onKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyUp && searchQuery.isNotBlank()) {
                                val pid = searchQuery.trim()
                                if (pid.isBlank() || (!pid.startsWith("P", ignoreCase = true) && !pid.all { it.isDigit() }))
                                    searchError = true
                                true
                            } else false
                        },
                )

                if (searchError) LaunchedEffect(searchError) { delay(15_000); searchError = false }

                Spacer(Modifier.height(HomeDesignTokens.SectionSpacing))

                // ── Kaomoji + encouragement ──
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.randomKaomoji,
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.encouragementText,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    )
                }

                Spacer(Modifier.height(40.dp))
            } // end left scrollable

            Spacer(Modifier.width(HomeDesignTokens.RowSpacing))

            // ── Right panel: cards stacked vertically, natural heights ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 280.dp)
                    .padding(end = 48.dp),
            ) {
                Spacer(Modifier.height(32.dp))

                RightPanelCard {
                    DailyProblemCard(
                        state = state.dailyProblemState,
                        onRefresh = onRefreshDailyProblem,
                        onViewDetail = onViewProblemDetail,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(HomeDesignTokens.RowSpacing))

                RightPanelCard {
                    ExtensionSlot(modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(40.dp))
            } // end right panel
        } // end Row

        // ── Bottom bar: Progress + Todos ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = HomeDesignTokens.BottomBarHPadding, vertical = HomeDesignTokens.BottomBarVPadding),
            ) {
                ContentCard(title = "📊 学习进度", modifier = Modifier.weight(0.55f)) {
                    StreakRow(streakDays = state.streakDays)
                    Spacer(Modifier.height(16.dp))
                    TopicProgressBar(topic = state.studyTopic, onUpdateTopic = onUpdateTopic)
                }
                Spacer(Modifier.width(16.dp))
                ContentCard(title = "📋 待办", modifier = Modifier.weight(0.45f)) {
                    TodoInput(onAdd = onAddTodo)
                    Spacer(Modifier.height(8.dp))
                    if (state.todos.isEmpty()) {
                        Text("暂无待办事项", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        state.todos.take(8).forEach { todo ->
                            TodoRow(todo = todo,
                                onToggle = { onToggleTodo(todo.id, todo.completed) },
                                onDelete = { onDeleteTodo(todo.id) })
                        }
                    }
                }
            }
        }
    } // end Column
}

// ═══════════════════════════════════════════════════════════════
// ContentCard (flat card wrapper)
// ═══════════════════════════════════════════════════════════════

@Composable
internal fun ContentCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Streak + Topic Progress
// ═══════════════════════════════════════════════════════════════

@Composable
internal fun StreakRow(streakDays: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🔥", fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "连续打卡 ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "$streakDays",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            " 天",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TopicProgressBar(
    topic: StudyTopic,
    onUpdateTopic: (String, Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editName by remember(topic.name) { mutableStateOf(topic.name) }
    var editGoal by remember(topic.goalCount) { mutableStateOf(topic.goalCount.toString()) }

    Column {
        if (editing) {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                placeholder = { Text("专题名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editGoal,
                    onValueChange = { editGoal = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("目标") },
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(4.dp))
                Text("题", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val goal = editGoal.toIntOrNull() ?: 10
                    onUpdateTopic(editName.ifBlank { "未命名专题" }, goal)
                    editing = false
                }) {
                    Text("保存", fontSize = 13.sp)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🎯",
                    fontSize = 18.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (topic.name.isBlank()) "设置学习专题" else topic.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = {
                        editName = topic.name
                        editGoal = topic.goalCount.toString()
                        editing = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("编辑", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            if (topic.goalCount > 0) {
                val progress = (topic.currentCount.toFloat() / topic.goalCount).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${topic.currentCount} / ${topic.goalCount} 题",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    "点击编辑设置目标题数",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Todo input + row
// ═══════════════════════════════════════════════════════════════

@Composable
internal fun ColumnScope.TodoInput(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("添加待办...", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).onKeyEvent { event ->
                if (event.key == Key.Enter && event.type == KeyEventType.KeyUp && text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                    true
                } else false
            },
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                FeatherIcons.Plus,
                contentDescription = "Add",
                modifier = Modifier.size(18.dp),
                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
internal fun TodoRow(
    todo: TodoItemDomainModel,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                if (todo.completed) FeatherIcons.CheckSquare else FeatherIcons.Square,
                contentDescription = if (todo.completed) "Mark incomplete" else "Mark complete",
                modifier = Modifier.size(18.dp),
                tint = if (todo.completed) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = todo.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
            color = if (todo.completed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                FeatherIcons.Trash2,
                contentDescription = "Delete",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// RightPanelCard — constrains child to natural height
// ═══════════════════════════════════════════════════════════════

/**
 * Wraps a card in the right panel, constraining it to its natural height.
 *
 * Uses [IntrinsicSize.Min] to prevent children with internal `fillMaxSize()`
 * (like [DailyProblemCard]) from stretching to fill the entire column height.
 *
 * Usage — add new cards to the right panel like this:
 * ```kotlin
 * RightPanelCard {
 *     YourNewCard(modifier = Modifier.fillMaxWidth())
 * }
 * ```
 */
@Composable
private fun RightPanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        content()
    }
}
