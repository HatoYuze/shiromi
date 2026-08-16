package com.github.hatoyuze.shiromi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.github.hatoyuze.luogu.gui.App
import com.github.hatoyuze.luogu.gui.config.GuiConfigLoader
import com.github.hatoyuze.luogu.gui.di.AppBootstrap
import com.github.hatoyuze.luogu.gui.platform.AppContextHolder

/**
 * Android 入口：先初始化平台上下文与全局引导（配置加载、日志、数据库），
 * 再进入 Compose 世界。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        val databaseWrapper = AppBootstrap(GuiConfigLoader()).initialize()
        setContent {
            App(databaseWrapper)
        }
    }
}
