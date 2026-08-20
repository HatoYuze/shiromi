// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.login

import top.kagg886.wvbridge.config.WebViewPlatformConfig
import top.kagg886.wvbridge.config.defaultPlatformConfig

// 注意：WebViewPlatformConfig 是 expect class（无公开构造器），
// 必须通过 defaultPlatformConfig() 工厂创建（与 wvbridge commonMain 的 WebViewConfig 一致）。
internal actual fun luoguWebViewPlatformConfig(): WebViewPlatformConfig = defaultPlatformConfig()
