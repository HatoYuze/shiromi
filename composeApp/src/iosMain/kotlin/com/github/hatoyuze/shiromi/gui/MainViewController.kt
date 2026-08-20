// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui

import androidx.compose.ui.window.ComposeUIViewController
import com.github.hatoyuze.shiromi.gui.config.GuiConfigLoader
import com.github.hatoyuze.shiromi.gui.di.AppBootstrap
import platform.UIKit.UIViewController

/**
 * iOS 入口：由 SwiftUI 的 `UIViewControllerRepresentable` 调起。
 *
 * 引导（配置、日志、数据库）在首个 Compose 帧之前完成，
 * 返回承载整个应用的 [UIViewController]。
 */
fun MainViewController(): UIViewController {
    val databaseWrapper = AppBootstrap(GuiConfigLoader()).initialize()
    return ComposeUIViewController { App(databaseWrapper) }
}
