// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.login

import com.github.hatoyuze.shiromi.gui.platform.cachePath
import okio.Path
import top.kagg886.wvbridge.config.WebViewPlatformConfig

internal actual fun luoguWebViewPlatformConfig(): WebViewPlatformConfig {
    val webviewRoot = cachePath / "webview"
    return WebViewPlatformConfig(
        windowSetting = WebViewPlatformConfig.Windows(dataDir = webviewRoot.toString()),
        linuxSetting = WebViewPlatformConfig.Linux(
            dataDir = (webviewRoot / "data").toString(),
            cacheDir = (webviewRoot / "cache").toString(),
        ),
        // macOS 用非持久化 store：会话只存内存（提取发生在对话框存活期内），
        // 避免登录态落在系统 WebKit 目录、无法随应用缓存一起清理。
        macOSSetting = WebViewPlatformConfig.MacOS(
            websiteDataStore = WebViewPlatformConfig.MacOS.WebsiteDataStore.NON_PERSISTENT,
        ),
    )
}
