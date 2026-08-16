package com.github.hatoyuze.luogu.gui

import androidx.compose.ui.window.ComposeUIViewController
import com.github.hatoyuze.luogu.gui.config.GuiConfigLoader
import com.github.hatoyuze.luogu.gui.di.AppBootstrap
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
