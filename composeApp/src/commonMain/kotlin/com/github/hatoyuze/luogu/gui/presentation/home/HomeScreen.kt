package com.github.hatoyuze.luogu.gui.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.luogu.gui.config.AppConfigStore
import com.github.hatoyuze.luogu.gui.config.ConfigService
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.gui.presentation.components.HomeDesignTokens
import com.github.hatoyuze.luogu.gui.presentation.components.ProblemDetailPage
import com.github.hatoyuze.luogu.gui.presentation.login.LuoguLoginDialog
import com.github.hatoyuze.luogu.gui.presentation.state.ChatEvent
import com.github.hatoyuze.luogu.gui.presentation.state.ChatViewModel
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel

/**
 * 桌面首页根（对齐设计稿「布局 3 优化版 A」）：
 * 左侧会话侧栏 + 右侧主内容（[HomeLayout]）；
 * 全窗口 overlay：事件编辑弹窗、题目详情、洛谷登录引导横幅。
 */
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by homeViewModel.state.collectAsState()

    // ── Overlay state (lifted to cover full window including sidebar) ──
    var problemDetailPid by remember { mutableStateOf<String?>(null) }
    var dialogContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var isDialogVisible by remember { mutableStateOf(false) }

    // ── 洛谷登录引导（首次运行 cookie 为空时）──
    var showLoginBanner by remember { mutableStateOf(ConfigService.luoguCookie.isBlank()) }
    var showLoginDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            HomeSidebar(
                sessions = state.sessions,
                recommendations = state.recommendations,
                onNewChat = { chatViewModel.handleEvent(ChatEvent.CreateNewSession(SessionType.CHAT)) },
                onNewCoach = { chatViewModel.handleEvent(ChatEvent.CreateNewSession(SessionType.COACH)) },
                onSelectSession = { chatViewModel.handleEvent(ChatEvent.SelectSession(it)) },
                onDeleteSession = { homeViewModel.deleteSession(it) },
                onSettings = onSettings,
                modifier = Modifier.width(HomeDesignTokens.SidebarWidth).fillMaxHeight(),
            )

            HorizontalDivider(
                modifier = Modifier.fillMaxHeight().width(1.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            )

            HomeLayout(
                state = state,
                onNavigateMonth = { homeViewModel.navigateMonth(it) },
                onSelectDate = { homeViewModel.selectDate(it) },
                onNavigateToToday = { homeViewModel.navigateToToday() },
                onAddTodo = { title, dueAt -> homeViewModel.addTodo(title, dueAt) },
                onToggleTodo = { id, completed -> homeViewModel.toggleTodo(id, completed) },
                onDeleteTodo = { homeViewModel.deleteTodo(it) },
                onAddEvent = { name, date, color, pinned, allDay, timeMinutes ->
                    homeViewModel.addCalendarEvent(name, date, color, pinned, allDay, timeMinutes)
                },
                onDeleteEvent = { homeViewModel.deleteCalendarEvent(it) },
                onUpdateTopic = { name, goal -> homeViewModel.updateStudyTopic(name, goal) },
                onRefreshDailyProblem = { homeViewModel.refreshDailyProblem() },
                onViewProblemDetail = { pid -> problemDetailPid = pid },
                showOverlay = { content -> dialogContent = content; isDialogVisible = true },
                hideOverlay = { isDialogVisible = false; dialogContent = null },
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
                        // 只有预取数据属于被搜索的题目时才复用，避免展示错题
                        prefetchedData = state.dailyProblemState.problemDetail?.takeIf {
                            it.problem.pid.equals(problemDetailPid, ignoreCase = true)
                        },
                        onBack = { problemDetailPid = null },
                        onCoachWithProblem = { pid ->
                            chatViewModel.handleEvent(ChatEvent.CreateNewSession(SessionType.COACH))
                            chatViewModel.handleEvent(ChatEvent.SendMessage("我想要学习 $pid 这道题"))
                        },
                    )
                }
            }
        }

        // ── 首次运行：未登录洛谷提示条 ──
        if (showLoginBanner) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 2.dp,
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "尚未登录洛谷，推荐先登录以使用完整功能",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showLoginDialog = true }) { Text("浏览器登录") }
                    TextButton(onClick = { showLoginBanner = false }) { Text("稍后") }
                }
            }
        }
    }

    val appConfigStore = org.koin.compose.koinInject<AppConfigStore>()
    if (showLoginDialog) {
        LuoguLoginDialog(
            onDismiss = { showLoginDialog = false },
            onSuccess = { session ->
                showLoginDialog = false
                showLoginBanner = false
                ConfigService.luoguCookie = session.cookieString
                session.uid?.takeIf { it > 0 }?.toString()?.let { ConfigService.luoguUid = it }
                try {
                    appConfigStore.save(ConfigService.toGuiConfig())
                } catch (_: Exception) {
                    // 持久化失败不阻塞登录成功反馈
                }
            },
        )
    }
}
