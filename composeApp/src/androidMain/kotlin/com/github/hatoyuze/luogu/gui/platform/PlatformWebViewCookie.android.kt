// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.platform

import android.webkit.CookieManager
import top.kagg886.wvbridge.WebViewController

/**
 * Android: wvbridge uses a plain `android.webkit.WebView` (default profile), so the
 * global [CookieManager] already holds the session cookies — including HttpOnly ones,
 * which `CookieManager.getCookie` returns as a `name=value; name=value` string.
 */
actual suspend fun getWebViewCookieString(controller: WebViewController<*>?, url: String): String? =
    CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
