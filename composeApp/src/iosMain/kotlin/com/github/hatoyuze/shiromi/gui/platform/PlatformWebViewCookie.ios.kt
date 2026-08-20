// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import top.kagg886.wvbridge.WebViewController

/** iOS: native cookie read not wired yet — extraction falls back to `document.cookie`. */
actual suspend fun getWebViewCookieString(controller: WebViewController<*>?, url: String): String? = null
