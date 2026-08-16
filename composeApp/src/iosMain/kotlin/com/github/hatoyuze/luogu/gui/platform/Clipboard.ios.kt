package com.github.hatoyuze.luogu.gui.platform

import platform.UIKit.UIPasteboard

actual suspend fun copyTextToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
