// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.hatoyuze.shiromi.gui.App
import com.github.hatoyuze.shiromi.gui.config.GuiConfigLoader
import com.github.hatoyuze.shiromi.gui.di.AppBootstrap
import com.github.hatoyuze.shiromi.gui.platform.AppContextHolder
import com.github.hatoyuze.shiromi.gui.platform.ImeMotionEngine

/**
 * Android 入口：先初始化平台上下文与全局引导（配置加载、日志、数据库），
 * 再进入 Compose 世界。
 *
 * IME 策略按版本区分（见 [setupWindowImeMode]）：API 30+（含 Android 15+ 强制
 * edge-to-edge）统一为“窗口不随键盘缩放、IME insets 派发”。本机（小米/Android 16）
 * 键盘升起期间平台派发的 animated inset 在 dock/float 两种形态下与可见键盘顶的
 * 映射不同（float 态 raw 会瞬间冲高后平台）。[ImeMotionEngine] 由 Compose 组合内
 * 的 ImeMotionInsetObserver 逐帧喂入，按形态输出校正值（见 ImeMotion.kt），
 * Compose 端 `smoothImePadding` 消费它；
 * API 28–29 保留 `adjustResize` 窗口缩放路径，避免旧版本输入区被键盘完全遮挡。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowImeMode()
        AppContextHolder.context = applicationContext
        ImeMotionEngine.install(window.decorView)
        val databaseWrapper = AppBootstrap(GuiConfigLoader()).initialize()
        setContent {
            App(databaseWrapper)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 失焦（回桌面/切后台/下拉通知栏）时键盘常以无动画方式收起、raw=0 帧可能缺失，
        // 清空校正值防输入区悬空；若键盘实际仍在（分屏），下一帧 inset 派发会立即恢复。
        if (!hasFocus) ImeMotionEngine.onWindowFocusLost()
    }

    /**
     * 设置窗口的 IME 模式：
     * - API 30+：edge-to-edge + `adjustNothing`，IME insets 派发给 Compose
     *   （`WindowInsets.ime` / `imePadding`），行为与 Android 15+ 强制模式一致。
     * - API 28–29：`adjustResize`，键盘弹出时窗口缩放（无 ime insets 时唯一的可用机制）。
     */
    private fun setupWindowImeMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enableEdgeToEdge()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
}
