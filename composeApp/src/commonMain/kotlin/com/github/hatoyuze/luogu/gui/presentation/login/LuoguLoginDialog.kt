@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.hatoyuze.luogu.gui.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.hatoyuze.luogu.gui.data.login.LuoguLoginVerifier
import com.github.hatoyuze.luogu.gui.data.login.LuoguSession
import com.github.hatoyuze.luogu.gui.data.login.LuoguSessionExtractor
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.kagg886.wvbridge.LoadingState
import top.kagg886.wvbridge.WebView
import top.kagg886.wvbridge.config.WebViewConfig
import top.kagg886.wvbridge.interceptor.InterceptorHandler
import top.kagg886.wvbridge.rememberWebViewController

/** 与 [com.github.hatoyuze.luogu.skill.api] 客户端保持一致的桌面 Chrome UA。 */
private const val LUOGU_WEBVIEW_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

private const val LUOGU_HOST = "www.luogu.com.cn"

/**
 * 判断是否为登录成功后的洛谷站内导航（host 精确匹配，排除登录页本身）。
 * 拒绝形似域名（如 `www.luogu.com.cn.evil.com`）。
 */
private fun isPostLoginNavigation(url: String): Boolean {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return false
    val rest = url.substring(schemeEnd + 3)
    val pathStart = rest.indexOf('/')
    val authority = if (pathStart < 0) rest else rest.substring(0, pathStart)
    val path = if (pathStart < 0) "" else rest.substring(pathStart)
    if (authority != LUOGU_HOST) return false
    return !path.startsWith("/auth/login")
}

private sealed interface LoginUiState {
    data object Browsing : LoginUiState
    data object Extracting : LoginUiState
    data class Error(val message: String) : LoginUiState
}

/**
 * 内嵌浏览器登录洛谷：打开 `/auth/login`，用户交互完成登录（含验证码/二次验证），
 * 提取会话 Cookie（含 HttpOnly 的 `__client_id`）并经真实 API 验证后回调 [onSuccess]。
 */
@Composable
fun LuoguLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: (LuoguSession) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val controller = rememberWebViewController(
                url = LuoguSessionExtractor.LOGIN_URL,
                config = WebViewConfig(
                    userAgent = LUOGU_WEBVIEW_UA,
                    platform = luoguWebViewPlatformConfig(),
                ),
            )
            val scope = rememberCoroutineScope()
            var state by remember { mutableStateOf<LoginUiState>(LoginUiState.Browsing) }
            var extractionRequested by remember { mutableIntStateOf(0) }

            fun triggerExtraction() {
                // 同步置位 Extracting，关闭 check-then-act 双触发窗口（仅在 UI 线程调用）。
                if (state == LoginUiState.Extracting) return
                state = LoginUiState.Extracting
                extractionRequested += 1
            }

            // 登录成功后洛谷会跳离 /auth/login（HAR 证实 referer 链：auth/login → 首页）；
            // 拦截到"离开登录页的 www.luogu.com.cn 导航"即自动触发提取。
            // 原生导航回调不在 UI 线程：只做轻量派发，状态读取与判定切回主线程执行。
            DisposableEffect(controller) {
                val registry = controller.interceptor.registerNavigationInterceptor { url ->
                    scope.launch {
                        if (state == LoginUiState.Browsing && isPostLoginNavigation(url)) {
                            triggerExtraction()
                        }
                    }
                    InterceptorHandler.Result.Allowed
                }
                onDispose { registry.close() }
            }

            LaunchedEffect(extractionRequested) {
                if (extractionRequested == 0) return@LaunchedEffect
                state = LoginUiState.Extracting
                // 等待跳转落地页加载完成（导航拦截在页面加载前触发，_feInjection 需页面解析完成），
                // 最多 5s；随后留少量余量让页面脚本收尾。
                var waitedMs = 0L
                while (controller.loadingState !is LoadingState.LoadingEnd && waitedMs < 5_000) {
                    delay(100)
                    waitedMs += 100
                }
                delay(300)
                state = try {
                    // 提取与验证都含阻塞式调用（native JNI / wvbridge evaluateScript / 网络），
                    // 放到后台 dispatcher 执行，避免冻结 UI；onSuccess 回调仍在主线程调用。
                    val session = withContext(Dispatchers.Default) { LuoguSessionExtractor.extract(controller) }
                    val verifiedUid = withContext(Dispatchers.Default) { LuoguLoginVerifier.verify(session) }
                    when {
                        session.cookieString.isBlank() ->
                            LoginUiState.Error("未能从浏览器读取 Cookie，请确认账号已登录成功后再试。")
                        !session.hasClientId ->
                            LoginUiState.Error("会话缺少 __client_id（HttpOnly 读取失败）。请重试，或点击右上角关闭后手动粘贴 Cookie。")
                        verifiedUid == null ->
                            LoginUiState.Error("登录验证失败：Cookie 可能不完整或已过期。请重新登录，或手动粘贴 Cookie。")
                        session.uid != null && session.uid != verifiedUid ->
                            LoginUiState.Error("登录用户不一致（期望 ${session.uid}，服务端 ${verifiedUid}）。请重新登录。")
                        else -> {
                            onSuccess(session.copy(uid = verifiedUid))
                            LoginUiState.Browsing
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e // 对话框销毁时取消，不当作错误状态
                } catch (e: Exception) {
                    LoginUiState.Error("提取或验证登录会话时出错：${e.message ?: e::class.simpleName}。请重试。")
                }
            }

            val progress by remember {
                derivedStateOf {
                    (controller.loadingState as? LoadingState.Loading)?.progress ?: -1f
                }
            }

            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("洛谷登录") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(FeatherIcons.ArrowLeft, contentDescription = "关闭")
                        }
                    },
                )
                if (progress in 0.0f..<1.0f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }

                Box(Modifier.weight(1f)) {
                    WebView(controller = controller, modifier = Modifier.fillMaxSize())

                    when (val s = state) {
                        LoginUiState.Browsing -> Unit
                        LoginUiState.Extracting -> OverlayCard {
                            CircularProgressIndicator()
                            Text("正在提取并验证登录会话…", style = MaterialTheme.typography.bodyMedium)
                        }
                        is LoginUiState.Error -> OverlayCard {
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onDismiss) { Text("关闭") }
                                Button(onClick = { triggerExtraction() }) { Text("重试导入") }
                            }
                        }
                    }
                }

                // 手动触发兜底（自动检测失败/用户在页内停留时）
                Surface(shadowElevation = 4.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { triggerExtraction() },
                            enabled = state != LoginUiState.Extracting,
                        ) {
                            Text("登录完成，导入 Cookie")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}
