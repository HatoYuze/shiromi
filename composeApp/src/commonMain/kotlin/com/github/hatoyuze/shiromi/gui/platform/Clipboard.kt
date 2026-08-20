// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

/**
 * Copies plain text to the system clipboard.
 *
 * Kept as a platform seam because the Compose `Clipboard` API on desktop only
 * exposes platform-specific [androidx.compose.ui.platform.ClipEntry] (see
 * JetBrains issue CMP-1260), which is unusable from commonMain for plain text.
 */
expect suspend fun copyTextToClipboard(text: String)
