package com.github.hatoyuze.luogu.gui.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual suspend fun copyTextToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
