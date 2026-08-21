// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.github.hatoyuze.shiromi.gui.config.ConfigService
import com.github.hatoyuze.shiromi.gui.data.local.DatabaseWrapper
import com.github.hatoyuze.shiromi.gui.di.commonModule
import com.github.hatoyuze.shiromi.gui.di.platformModule
import com.github.hatoyuze.shiromi.gui.presentation.ChatScreen
import com.github.hatoyuze.shiromi.gui.presentation.MobileHomeScreen
import com.github.hatoyuze.shiromi.gui.presentation.SettingsScreen
import com.github.hatoyuze.shiromi.gui.presentation.adaptive.PlatformSizeClass
import com.github.hatoyuze.shiromi.gui.presentation.adaptive.calculatePlatformSizeClass
import com.github.hatoyuze.shiromi.gui.presentation.home.HomeScreen
import com.github.hatoyuze.shiromi.gui.presentation.onboarding.OnboardingScreen
import com.github.hatoyuze.shiromi.gui.presentation.state.ChatViewModel
import com.github.hatoyuze.shiromi.gui.presentation.state.HomeViewModel
import com.github.hatoyuze.shiromi.gui.theme.LuoguTheme
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
 * 底栏隐藏状态：hide 与 show 都用 8dp（与 HEAD 一致）。键盘升起时底栏立即消失，
 * 键盘收起时底栏带 enter 动画恢复。IME inset 读取是 @Composable 的（CMP 1.11），
 * 限定在本小 Composable 内，键盘动画帧只重组这里，不波及 MobileNav。
 */
@Composable
private fun rememberImeNavHidden(): Boolean {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var hidden by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom) {
        val hidePx = with(density) { imeShowThresholdDp.toPx() }
        val showPx = with(density) { imeHideThresholdDp.toPx() }
        if (imeBottom > hidePx) hidden = true
        else if (imeBottom <= showPx) hidden = false
    }
    return hidden
}

/**
 * 键盘“可见”判定阈值：IME inset ≤ 该值才恢复显示底部导航栏（与 HEAD 一致）。
 * 与 [OnboardingScreen] 共用（同一策略）。
 */
internal val imeHideThresholdDp = 8.dp

/** 键盘“明显升起”阈值：IME inset > 该值立即隐藏底部导航栏（与 HEAD 一致）。 */
internal val imeShowThresholdDp = 8.dp

@Composable
private fun MobileNav() {
    val chatViewModel = koinInject<ChatViewModel>()
    val homeViewModel = koinInject<HomeViewModel>()
    var dest by rememberSaveable { mutableStateOf(MobileDest.HOME) }

    // IME 可见时隐藏底部导航栏：底栏位于输入框与窗口底边之间（内容列始终结束于
    // 底栏之上），键盘弹出时 imePadding 会把输入框抬得过高，留下约一个底栏高度的
    // 空档。隐藏后内容可延伸至窗口底边，输入框经各输入面自己的 imePadding 恰好
    // 落在键盘顶边。
    // 底栏隐藏状态独立小作用域管理：IME inset 读取（@Composable）与滞回判定只重组
    // rememberImeNavHidden 自身，键盘动画帧不波及 MobileNav；MobileNav 仅在
    // hidden 翻转（每轮键盘最多两次）时重组。
    val imeNavHidden = rememberImeNavHidden()

    // contentWindowInsets 只取顶部状态栏：底部系统栏 inset 在底栏可见时由其自身
    // 处理，在底栏因 IME 隐藏时被键盘覆盖，避免输入区被系统栏/IME 重复抬高。
    Scaffold(
        bottomBar = {
            // 方向敏感：键盘升起时 exit=None 立即隐藏（与 HEAD 一致，避免 Scaffold
            // 高度动画与 IME 上升叠加）；键盘收起时 enter 滑入保留丝滑恢复。
            AnimatedVisibility(
                visible = !imeNavHidden,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = ExitTransition.None,
            ) {
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
