package com.github.hatoyuze.luogu.gui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.hatoyuze.luogu.gui.config.GuiConfigLoader
import com.github.hatoyuze.luogu.gui.di.AppBootstrap

fun main() = application {
    val databaseWrapper = AppBootstrap(GuiConfigLoader()).initialize()
    Window(
        onCloseRequest = ::exitApplication,
        title = "Shiromi",
        state = rememberWindowState(width = 1500.dp, height = 1320.dp),
    ) {
        App(databaseWrapper)
    }
}
