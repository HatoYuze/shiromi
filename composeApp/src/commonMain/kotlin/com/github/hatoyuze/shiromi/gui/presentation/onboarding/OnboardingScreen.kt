// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.hatoyuze.shiromi.gui.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.shiromi.gui.config.AppConfigStore
import com.github.hatoyuze.shiromi.gui.config.ConfigService
import com.github.hatoyuze.shiromi.gui.data.Logger
import com.github.hatoyuze.shiromi.gui.data.log.LogCategory
import com.github.hatoyuze.shiromi.gui.imeHideThresholdDp
import com.github.hatoyuze.shiromi.gui.platform.ioDispatcher
import com.github.hatoyuze.shiromi.gui.presentation.adaptive.PlatformSizeClass
import com.github.hatoyuze.shiromi.gui.presentation.adaptive.calculatePlatformSizeClass
import com.github.hatoyuze.shiromi.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.shiromi.gui.presentation.login.LuoguLoginDialog
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronLeft
import compose.icons.feathericons.ChevronRight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource
import org.koin.compose.koinInject

private const val PAGE_COUNT = 4

private val OkColor = Color(0xFF2E9E5B)
private val WarnColor = Color(0xFFD98F68)

/**
 * 启动引导：4 页分页向导（欢迎 / Luogu Cookie / Deepseek API Key / 完成）。
 *
 * 翻页交互：移动端左右滑动；桌面端 `‹ ›` 图标 + 鼠标滚轮（页面内容可滚动时先滚动内容，
 * 到达边界才切页，350ms 冷却防连跳）。底部圆点指示：当前页白、已访问 75% 白、后续 38% 灰。
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = koinInject<AppConfigStore>()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val isDesktop = calculatePlatformSizeClass() == PlatformSizeClass.Expanded
    val snackbarHostState = remember { SnackbarHostState() }

    var apiKeyInput by remember { mutableStateOf(ConfigService.apiKey) }
    var cookieInput by remember { mutableStateOf(ConfigService.luoguCookie) }
    var cookieUid by remember { mutableStateOf(ConfigService.luoguUid.toIntOrNull()) }
    var showLoginDialog by remember { mutableStateOf(false) }

    val apiKeyValid = isValidApiKey(apiKeyInput)
    val cookieConfigured = looksLikeLuoguCookie(cookieInput)

    /**
     * 持久化配置。保存走 IO dispatcher（磁盘 + 加密）；失败记录日志（不含凭证）并返回 false。
     * [onlyValid] 为 true 时（跳过路径）只持久化格式有效的字段，避免无效输入锁死下次引导。
     */
    suspend fun persist(onlyValid: Boolean = false): Boolean {
        if (!onlyValid || apiKeyValid) ConfigService.apiKey = apiKeyInput.trim()
        if (!onlyValid || cookieConfigured) ConfigService.luoguCookie = cookieInput.trim()
        cookieUid?.takeIf { it > 0 }?.let { ConfigService.luoguUid = it.toString() }
        return try {
            withContext(ioDispatcher) { store.save(ConfigService.toGuiConfig()) }
            true
        } catch (e: Exception) {
            Logger.info(LogCategory.CONFIG, "onboarding.save", "persist failed: ${e.message}")
            false
        }
    }

    // IME 可见时隐藏底部操作栏 + 分页内容 imePadding（与 MobileNav 同一套策略）：
    // 底栏会夹在输入框与窗口底边之间，键盘弹出时 imePadding 会把输入框抬得过高；
    // 隐藏底栏后内容延伸到底部，输入框恰好落在键盘顶边。
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > with(density) { imeHideThresholdDp.toPx() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!imeVisible) {
                OnboardingBottomBar(
                    pagerState = pagerState,
                    isDesktop = isDesktop,
                    showActions = pagerState.currentPage == PAGE_COUNT - 1,
                    startEnabled = apiKeyValid,
                    onPrev = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    onDotClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                    onStart = {
                        scope.launch {
                            if (persist(onlyValid = false)) {
                                onDone()
                            } else {
                                snackbarHostState.showSnackbar("配置保存失败，请重试")
                            }
                        }
                    },
                    onSkip = {
                        scope.launch {
                            persist(onlyValid = true)
                            onDone()
                        }
                    },
                )
            }
        },
        // 只取顶部状态栏：底部 inset 在操作栏可见时由其自身处理，隐藏时被键盘覆盖
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
        ) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .wheelPageSwitch(pagerState, scope)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> CookiePage(
                        cookieInput = cookieInput,
                        cookieConfigured = cookieConfigured,
                        onCookieChange = { value ->
                            cookieInput = value
                            extractLuoguUid(value)?.let { cookieUid = it }
                        },
                        onBrowserLogin = { showLoginDialog = true },
                    )
                    2 -> ApiKeyPage(
                        apiKeyInput = apiKeyInput,
                        apiKeyValid = apiKeyValid,
                        onApiKeyChange = { apiKeyInput = it },
                    )
                    else -> DonePage(
                        cookieConfigured = cookieConfigured,
                        cookieUid = cookieUid,
                        apiKeyConfigured = apiKeyValid,
                        apiKeyInput = apiKeyInput,
                        onGoto = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                    )
                }
            }
        }
    }

    if (showLoginDialog) {
        LuoguLoginDialog(
            onDismiss = { showLoginDialog = false },
            onSuccess = { session ->
                showLoginDialog = false
                cookieInput = session.cookieString
                cookieUid = session.uid
                scope.launch { persist() }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 滚轮翻页（桌面端）
// ═══════════════════════════════════════════════════════════════

/**
 * 鼠标滚轮翻页：挂在每页的 verticalScroll 之后（scrollable 更外层）。
 * scrollable 消费滚动（内容可滚）时不切页；内容抵达边界（scrollable 不再消费）
 * 时由本处理器消费并切换页面；350ms 冷却防连跳。
 */
private fun Modifier.wheelPageSwitch(pagerState: PagerState, scope: CoroutineScope): Modifier =
    pointerInput(pagerState) {
        var lastSwitch = TimeSource.Monotonic.markNow()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull {
                    it.type == PointerType.Mouse && it.scrollDelta.y != 0f
                } ?: continue
                if (change.isConsumed) continue // 页面内容正在滚动
                val delta = change.scrollDelta.y
                if (lastSwitch.elapsedNow().inWholeMilliseconds < 350) {
                    change.consume()
                    continue
                }
                val target = pagerState.currentPage + if (delta > 0) 1 else -1
                if (target !in 0 until pagerState.pageCount) {
                    change.consume()
                    continue
                }
                change.consume()
                lastSwitch = TimeSource.Monotonic.markNow()
                scope.launch { pagerState.animateScrollToPage(target) }
            }
        }
    }

// ═══════════════════════════════════════════════════════════════
// 底部控制条
// ═══════════════════════════════════════════════════════════════

@Composable
private fun OnboardingBottomBar(
    pagerState: PagerState,
    isDesktop: Boolean,
    showActions: Boolean,
    startEnabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDotClick: (Int) -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(
        // 与 Scaffold 背景同色、无高度阴影，避免底部栏在浅色主题下
        // （surface #FDFBF7 vs background #F5F1EB）形成突兀的色带。
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        // Android 15+ 强制 edge-to-edge：底部栏内容必须让出系统导航栏
        // （3 键导航 返回/Home/后台 是透明浮层），否则圆点栏会被其盖住。
        val bottomInsetModifier = Modifier.fillMaxWidth().navigationBarsPadding()
        if (showActions) {
            // 末页：圆点导航靠左，动作按钮靠右，避免窄屏重叠
            Row(
                bottomInsetModifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NavCluster(pagerState, isDesktop, onPrev, onNext, onDotClick)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onSkip) { Text("稍后") }
                    Button(onClick = onStart, enabled = startEnabled) { Text("开始使用") }
                }
            }
        } else {
            Box(
                bottomInsetModifier.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                NavCluster(pagerState, isDesktop, onPrev, onNext, onDotClick)
            }
        }
    }
}

@Composable
private fun NavCluster(
    pagerState: PagerState,
    isDesktop: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDotClick: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isDesktop) {
            IconButton(onClick = onPrev, enabled = pagerState.currentPage > 0) {
                Icon(FeatherIcons.ChevronLeft, contentDescription = "上一页")
            }
        }
        DotsIndicator(pagerState, onDotClick)
        if (isDesktop) {
            IconButton(
                onClick = onNext,
                enabled = pagerState.currentPage < pagerState.pageCount - 1,
            ) {
                Icon(FeatherIcons.ChevronRight, contentDescription = "下一页")
            }
        }
    }
}

@Composable
private fun DotsIndicator(pagerState: PagerState, onDotClick: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(pagerState.pageCount) { i ->
            val active = i == pagerState.currentPage
            val visited = i < pagerState.currentPage
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = "第 ${i + 1} 页" }
                    .clickable { onDotClick(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (active) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> Color.White
                                visited -> Color.White.copy(alpha = 0.75f)
                                else -> Color.White.copy(alpha = 0.38f)
                            },
                        ),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 页面内容
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WelcomePage() {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "👋 欢迎使用 Shiromi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "基于 Deepseek 的 OI 教学引导 Agent",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "使用前需完成以下两项配置，以分别调用洛谷接口与 Deepseek 推理服务。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        StepCard(1, "Luogu Cookie", "用于洛谷接口调用；凭证仅存储在本地")
        Spacer(Modifier.height(12.dp))
        StepCard(2, "Deepseek API Key", "用于 Agent 推理与引导能力")
    }
}

@Composable
private fun StepCard(num: Int, title: String, desc: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$num",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CookiePage(
    cookieInput: String,
    cookieConfigured: Boolean,
    onCookieChange: (String) -> Unit,
    onBrowserLogin: () -> Unit,
) {
    var pasteExpanded by remember { mutableStateOf(cookieInput.isNotBlank()) }
    Column(Modifier.fillMaxSize()) {
        SectionTitle("① Luogu Cookie")
        Spacer(Modifier.height(6.dp))
        Text(
            "请先提供您的 Luogu Cookie（该凭证仅存储在本地，专用于 luogu-api 调用）。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OptionCard(
            icon = { Icon(OnboardingIcons.IcBrowser, null, tint = MaterialTheme.colorScheme.primary) },
            title = "方式一：使用内置浏览器自行登录",
            desc = "在应用内打开洛谷登录页，登录后自动导入会话 Cookie（含 UID）。",
            configured = cookieConfigured,
            onClick = onBrowserLogin,
        )
        Spacer(Modifier.height(12.dp))
        OptionCard(
            icon = { Icon(OnboardingIcons.IcCookieList, null, tint = MaterialTheme.colorScheme.primary) },
            title = "方式二：使用已获取的 Cookie 手动粘贴",
            desc = "从浏览器 DevTools 复制完整 Cookie（含 _uid=）粘贴到下方。",
            configured = cookieConfigured,
            onClick = { pasteExpanded = !pasteExpanded },
        ) {
            if (pasteExpanded) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    SensitiveInput(
                        value = cookieInput,
                        onValueChange = onCookieChange,
                        placeholder = "_uid=...; __client_id=...; C3VK=...",
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "提示：DevTools → Application → Cookies → www.luogu.com.cn，复制全部 Cookie 项。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
    configured: Boolean,
    onClick: () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (configured) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.5.dp,
            if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (configured) {
                    Icon(
                        AppIcons.SuccessIcon,
                        contentDescription = "已配置",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        "›",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (configured) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        AppIcons.SuccessIcon,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "已配置",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            extra?.invoke(this)
        }
    }
}

@Composable
private fun ApiKeyPage(
    apiKeyInput: String,
    apiKeyValid: Boolean,
    onApiKeyChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SectionTitle("② Deepseek API Key")
        Spacer(Modifier.height(6.dp))
        Text(
            "此外，您还需要提供一个有效的 Deepseek API Key 以获得 Agent 支持。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SensitiveInput(
            value = apiKeyInput,
            onValueChange = onApiKeyChange,
            placeholder = "sk-...",
            isError = apiKeyInput.isNotBlank() && !apiKeyValid,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                apiKeyValid -> {
                    Icon(
                        AppIcons.SuccessIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = OkColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "API Key 已填写（格式有效，使用时再验证）",
                        style = MaterialTheme.typography.labelMedium,
                        color = OkColor,
                    )
                }
                apiKeyInput.isNotBlank() -> {
                    Icon(
                        AppIcons.CloseIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "请填入以 sk- 开头的有效 Key",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Text(
                    "将用于 Deepseek 推理服务",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DonePage(
    cookieConfigured: Boolean,
    cookieUid: Int?,
    apiKeyConfigured: Boolean,
    apiKeyInput: String,
    onGoto: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SectionTitle("完成配置")
        Spacer(Modifier.height(6.dp))
        Text(
            "完成以上两项配置后，即可开始使用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        SummaryRow(
            ok = cookieConfigured,
            title = "Luogu Cookie",
            desc = if (cookieConfigured) "已配置（UID: ${cookieUid ?: "—"}）" else "未配置",
            onGoto = { onGoto(1) },
        )
        Spacer(Modifier.height(10.dp))
        SummaryRow(
            ok = apiKeyConfigured,
            title = "Deepseek API Key",
            desc = if (apiKeyConfigured) {
                val tail = apiKeyInput.trim().takeLast(4)
                "已填写（sk-…$tail）"
            } else {
                "未配置"
            },
            onGoto = { onGoto(2) },
        )
        if (!cookieConfigured) {
            Spacer(Modifier.height(14.dp))
            Text(
                "未配置 Cookie：洛谷功能（题目检索 / Coach 等）暂不可用，可在设置页补充。",
                modifier = Modifier.fillMaxWidth(),
                color = WarnColor,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SummaryRow(ok: Boolean, title: String, desc: String, onGoto: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (ok) OkColor.copy(alpha = 0.15f) else WarnColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (ok) {
                    Icon(
                        AppIcons.SuccessIcon,
                        contentDescription = "已完成",
                        modifier = Modifier.size(16.dp),
                        tint = OkColor,
                    )
                } else {
                    Text(
                        "!",
                        color = WarnColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!ok) {
                TextButton(onClick = onGoto) { Text("去配置") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 通用小组件
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SensitiveInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) AppIcons.EyeClose else AppIcons.EyeOpen,
                    contentDescription = if (visible) "隐藏" else "显示",
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
    )
}
