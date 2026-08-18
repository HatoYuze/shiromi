package com.github.hatoyuze.luogu.gui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.luogu.gui.config.ConfigService
import com.github.hatoyuze.luogu.gui.data.local.DatabaseWrapper
import com.github.hatoyuze.luogu.gui.di.commonModule
import com.github.hatoyuze.luogu.gui.di.platformModule
import com.github.hatoyuze.luogu.gui.presentation.ChatScreen
import com.github.hatoyuze.luogu.gui.presentation.MobileHomeScreen
import com.github.hatoyuze.luogu.gui.presentation.SettingsScreen
import com.github.hatoyuze.luogu.gui.presentation.adaptive.PlatformSizeClass
import com.github.hatoyuze.luogu.gui.presentation.adaptive.calculatePlatformSizeClass
import com.github.hatoyuze.luogu.gui.presentation.home.HomeScreen
import com.github.hatoyuze.luogu.gui.presentation.onboarding.OnboardingScreen
import com.github.hatoyuze.luogu.gui.presentation.state.ChatViewModel
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel
import com.github.hatoyuze.luogu.gui.theme.LuoguTheme
import compose.icons.FeatherIcons
import compose.icons.feathericons.BookOpen
import compose.icons.feathericons.MessageSquare
import compose.icons.feathericons.Settings
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@Composable
fun App(databaseWrapper: DatabaseWrapper) {
    LuoguTheme {
        KoinApplication(application = {
            modules(commonModule(), platformModule(databaseWrapper))
        }) {
            AppNav()
        }
    }
}

@Composable
private fun AppNav() {
    // 启动引导：apiKey 或 luoguCookie 为空时先完成配置（本次会话内可跳过/完成一次）。
    var onboardingFinished by rememberSaveable { mutableStateOf(false) }
    val needsOnboarding = !onboardingFinished &&
        (ConfigService.apiKey.isBlank() || ConfigService.luoguCookie.isBlank())

    if (needsOnboarding) {
        OnboardingScreen(onDone = { onboardingFinished = true })
        return
    }

    when (calculatePlatformSizeClass()) {
        PlatformSizeClass.Expanded -> DesktopNav()
        PlatformSizeClass.Compact -> MobileNav()
    }
}

// ═══════════════════════════════════════════════════════════════
// 桌面导航：原侧栏布局（≥600dp，行为与历史版本一致）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DesktopNav() {
    val chatViewModel = koinInject<ChatViewModel>()
    val homeViewModel = koinInject<HomeViewModel>()
    val chatState by chatViewModel.state.collectAsState()

    var showSettings by remember { mutableStateOf(false) }

    when {
        showSettings -> {
            SettingsScreen(
                onBack = { showSettings = false },
            )
        }
        chatState.showHomeScreen -> {
            HomeScreen(
                homeViewModel = homeViewModel,
                chatViewModel = chatViewModel,
                onSettings = { showSettings = true },
            )
        }
        else -> {
            ChatScreen(viewModel = chatViewModel)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 手机导航：底部三栏（首页 / 对话 / 设置）
// ═══════════════════════════════════════════════════════════════

private enum class MobileDest { HOME, CHAT, SETTINGS }

/**
 * 键盘“可见”判定阈值：IME inset 高于该值才隐藏底部导航栏，低于则视为键盘已收起，
 * 避免键盘动画收尾阶段底栏闪跳。与 [OnboardingScreen] 共用（同一策略）。
 */
internal val imeHideThresholdDp = 8.dp

@Composable
private fun MobileNav() {
    val chatViewModel = koinInject<ChatViewModel>()
    val homeViewModel = koinInject<HomeViewModel>()
    var dest by rememberSaveable { mutableStateOf(MobileDest.HOME) }

    // IME 可见时隐藏底部导航栏：底栏位于输入框与窗口底边之间（内容列始终结束于
    // 底栏之上），键盘弹出时 imePadding 会把输入框抬得过高，留下约一个底栏高度的
    // 空档。隐藏后内容可延伸至窗口底边，输入框经各输入面自己的 imePadding 恰好
    // 落在键盘顶边。读取 insets 触发重组与 imePadding 同一机制，动画帧内重组成本
    // 有限（子节点参数稳定时被 strong-skipping 跳过）。
    val density = LocalDensity.current
    val imeHideThresholdPx = with(density) { imeHideThresholdDp.toPx() }
    val imeVisible = WindowInsets.ime.getBottom(density) > imeHideThresholdPx

    // contentWindowInsets 只取顶部状态栏：底部系统栏 inset 在底栏可见时由其自身
    // 处理，在底栏因 IME 隐藏时被键盘覆盖，避免输入区被系统栏/IME 重复抬高。
    Scaffold(
        bottomBar = {
            if (!imeVisible) {
                NavigationBar {
                    NavigationBarItem(
                        selected = dest == MobileDest.HOME,
                        onClick = { dest = MobileDest.HOME },
                        icon = { Icon(FeatherIcons.BookOpen, contentDescription = null) },
                        label = { Text("首页") },
                    )
                    NavigationBarItem(
                        selected = dest == MobileDest.CHAT,
                        onClick = { dest = MobileDest.CHAT },
                        icon = { Icon(FeatherIcons.MessageSquare, contentDescription = null) },
                        label = { Text("对话") },
                    )
                    NavigationBarItem(
                        selected = dest == MobileDest.SETTINGS,
                        onClick = { dest = MobileDest.SETTINGS },
                        icon = { Icon(FeatherIcons.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (dest) {
                MobileDest.HOME -> MobileHomeScreen(
                    homeViewModel = homeViewModel,
                    chatViewModel = chatViewModel,
                    onOpenChat = { dest = MobileDest.CHAT },
                    onSettings = { dest = MobileDest.SETTINGS },
                )
                MobileDest.CHAT -> ChatScreen(
                    viewModel = chatViewModel,
                    onBack = { dest = MobileDest.HOME },
                )
                MobileDest.SETTINGS -> SettingsScreen(
                    onBack = { dest = MobileDest.HOME },
                )
            }
        }
    }
}
