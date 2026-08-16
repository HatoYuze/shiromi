package com.github.hatoyuze.luogu.gui.platform

/**
 * iOS 沙盒内无「文件管理器打开目录」的系统交互，当前实现为 no-op。
 */
actual fun openDirectory(path: String) {
    // best-effort: no-op on iOS
}
