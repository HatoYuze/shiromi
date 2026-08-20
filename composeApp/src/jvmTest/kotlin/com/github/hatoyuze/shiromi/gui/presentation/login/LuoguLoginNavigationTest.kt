// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.login

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuoguLoginNavigationTest {

    @Test fun `auth flow paths are not post-login navigation`() {
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn/auth/login"))
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn/auth/login?type=qq"))
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn/auth"))
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn/auth/unlock"))
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn/auth/oauth/qq"))
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn/auth/oauth/callback?code=abc&state=1"))
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn//auth/login")) // 双斜杠
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn/Auth/login")) // 大小写变体
    }

    @Test fun `regular luogu pages are post-login navigation`() {
        assertTrue(isPostLoginNavigation("https://www.luogu.com.cn"))
        assertTrue(isPostLoginNavigation("https://www.luogu.com.cn/"))
        assertTrue(isPostLoginNavigation("https://www.luogu.com.cn/?t=1"))
        assertTrue(isPostLoginNavigation("https://www.luogu.com.cn/problem/P1000"))
        assertTrue(isPostLoginNavigation("https://www.luogu.com.cn/user/settings"))
        // 非 auth 前缀（如真实存在的其它路径）不应被大小写归一误伤
        assertTrue(isPostLoginNavigation("https://www.luogu.com.cn/AUTHENTICATION/foo"))
    }

    @Test fun `external and lookalike hosts are not post-login navigation`() {
        assertFalse(isPostLoginNavigation("https://graph.qq.com/oauth2.0/show"))
        assertFalse(isPostLoginNavigation("https://open.weixin.qq.com/connect/qrconnect"))
        assertFalse(isPostLoginNavigation("https://www.luogu.com.cn.evil.com/auth/login"))
        assertFalse(isPostLoginNavigation("https://evil.com/www.luogu.com.cn/"))
        assertFalse(isPostLoginNavigation("www.luogu.com.cn/")) // 无 scheme
        assertFalse(isPostLoginNavigation(""))
    }

    @Test fun `shouldTriggerExtraction requires both url gate and logged-in cookie`() {
        // auth 路径即使有 uid 也不触发
        assertFalse(shouldTriggerExtraction("https://www.luogu.com.cn/auth/oauth/callback?code=x", "_uid=123"))
        // 普通页但无会话不触发
        assertFalse(shouldTriggerExtraction("https://www.luogu.com.cn/", null))
        assertFalse(shouldTriggerExtraction("https://www.luogu.com.cn/", ""))
        assertFalse(shouldTriggerExtraction("https://www.luogu.com.cn/", "_uid=0; __client_id=abc"))
        // 普通页 + 已登录 uid 才触发
        assertTrue(shouldTriggerExtraction("https://www.luogu.com.cn/", "_uid=123; __client_id=abc"))
        assertTrue(shouldTriggerExtraction("https://www.luogu.com.cn/problem/P1000", "_uid=42"))
    }
}
