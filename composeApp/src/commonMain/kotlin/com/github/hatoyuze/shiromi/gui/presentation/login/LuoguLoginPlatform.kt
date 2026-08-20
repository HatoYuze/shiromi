// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.login

import top.kagg886.wvbridge.config.WebViewPlatformConfig

/**
 * 洛谷登录 WebView 的平台配置。
 *
 * - JVM 桌面：将引擎数据/缓存隔离到 `~/.luogu-gui/cache/webview`（登录态可清理、不污染系统默认目录）。
 * - Android / iOS：平台默认配置。
 */
internal expect fun luoguWebViewPlatformConfig(): WebViewPlatformConfig
