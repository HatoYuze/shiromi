package com.github.hatoyuze.luogu.gui.platform

/**
 * Copies plain text to the system clipboard.
 *
 * Kept as a platform seam because the Compose `Clipboard` API on desktop only
 * exposes platform-specific [androidx.compose.ui.platform.ClipEntry] (see
 * JetBrains issue CMP-1260), which is unusable from commonMain for plain text.
 */
expect suspend fun copyTextToClipboard(text: String)
