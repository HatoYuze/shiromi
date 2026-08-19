// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.platform

import platform.UIKit.UIPasteboard

actual suspend fun copyTextToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
