package com.github.hatoyuze.shiromi

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.hatoyuze.luogu.gui.App
import com.github.hatoyuze.luogu.gui.config.GuiConfigLoader
import com.github.hatoyuze.luogu.gui.di.AppBootstrap
import com.github.hatoyuze.luogu.gui.platform.AppContextHolder

/**
 * Android 入口：先初始化平台上下文与全局引导（配置加载、日志、数据库），
 * 再进入 Compose 世界。
 *
 * IME 策略按版本区分（见 [setupWindowImeMode]）：API 30+（含 Android 15+ 强制
 * edge-to-edge）统一为“窗口不随键盘缩放、IME insets 派发”，`imePadding()` 成为
 * 输入区唯一的键盘偏移来源；API 28–29 保留 `adjustResize` 窗口缩放路径，避免
 * 旧版本没有 ime insets 派发时输入区被键盘完全遮挡。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowImeMode()
        AppContextHolder.context = applicationContext
        val databaseWrapper = AppBootstrap(GuiConfigLoader()).initialize()
        setContent {
            App(databaseWrapper)
        }
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
