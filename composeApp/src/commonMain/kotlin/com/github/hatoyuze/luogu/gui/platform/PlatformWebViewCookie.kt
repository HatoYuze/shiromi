// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.platform

import top.kagg886.wvbridge.WebViewController

/**
 * Reads the cookie header string (`name=value; name=value`, HttpOnly included)
 * for [url] from the embedded WebView session.
 *
 * - JVM desktop: wvbridge fork native cookie read (`getCookieString`).
 * - Android: `android.webkit.CookieManager` (default profile, zero wvbridge change).
 * - iOS: not wired yet, returns `null` (extraction falls back to `document.cookie`).
 */
expect suspend fun getWebViewCookieString(controller: WebViewController<*>?, url: String): String?
