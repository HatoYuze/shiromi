package com.github.hatoyuze.luogu.gui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.github.hatoyuze.luogu.gui.data.local.DatabaseWrapper
import com.github.hatoyuze.luogu.gui.di.commonModule
import com.github.hatoyuze.luogu.gui.di.platformModule
import com.github.hatoyuze.luogu.gui.presentation.ChatScreen
import com.github.hatoyuze.luogu.gui.presentation.HomeScreen
import com.github.hatoyuze.luogu.gui.presentation.MobileHomeScreen
import com.github.hatoyuze.luogu.gui.presentation.SettingsScreen
import com.github.hatoyuze.luogu.gui.presentation.adaptive.PlatformSizeClass
import com.github.hatoyuze.luogu.gui.presentation.adaptive.calculatePlatformSizeClass
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

@Composable
private fun MobileNav() {
    val chatViewModel = koinInject<ChatViewModel>()
    val homeViewModel = koinInject<HomeViewModel>()
    var dest by rememberSaveable { mutableStateOf(MobileDest.HOME) }

    Scaffold(
        bottomBar = {
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
        },
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
