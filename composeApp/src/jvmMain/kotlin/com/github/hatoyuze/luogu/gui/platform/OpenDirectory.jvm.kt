package com.github.hatoyuze.luogu.gui.platform

actual fun openDirectory(path: String) {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    val command = when {
        os.contains("win") -> listOf("explorer.exe", path)
        os.contains("mac") -> listOf("open", path)
        else -> listOf("xdg-open", path)
    }
    try {
        ProcessBuilder(command).start()
    } catch (_: Exception) {
        // best-effort
    }
}
