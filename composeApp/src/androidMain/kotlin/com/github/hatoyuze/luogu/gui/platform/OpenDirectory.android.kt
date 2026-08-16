package com.github.hatoyuze.luogu.gui.platform

import android.content.Intent
import android.net.Uri
import java.io.File

actual fun openDirectory(path: String) {
    // 尽力而为：系统文件管理器不保证支持 file:// URI，失败时静默忽略
    try {
        val uri = Uri.fromFile(File(path))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        AppContextHolder.context.startActivity(intent)
    } catch (_: Exception) {
        // best-effort
    }
}
