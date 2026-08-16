package com.github.hatoyuze.luogu.gui.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import top.kagg886.wvbridge.WebViewController
import top.kagg886.wvbridge.getCookieString

/**
 * JVM desktop: native WebView cookie read via the vendored wvbridge fork.
 *
 * 线程模型：native 读取是阻塞式 JNI（内部自行切换到引擎线程），因此放到 [Dispatchers.IO]
 * 上执行，避免阻塞 UI 线程；[withTimeout] 兜底回调丢失/引擎挂起，避免永久卡死。
 */
actual suspend fun getWebViewCookieString(controller: WebViewController<*>?, url: String): String? {
    if (controller == null) return null
    return withContext(Dispatchers.IO) {
        withTimeout(5_000) { controller.getCookieString(url) }
    }
}
