package com.github.hatoyuze.luogu.gui.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

actual suspend fun copyTextToClipboard(text: String) {
    val manager = AppContextHolder.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("shiromi", text))
}
