package com.github.hatoyuze.luogu.gui.platform

import top.kagg886.wvbridge.WebViewController

/** iOS: native cookie read not wired yet — extraction falls back to `document.cookie`. */
actual suspend fun getWebViewCookieString(controller: WebViewController<*>?, url: String): String? = null
