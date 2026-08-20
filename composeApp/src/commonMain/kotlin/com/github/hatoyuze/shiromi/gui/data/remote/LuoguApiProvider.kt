// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.remote

import com.github.hatoyuze.shiromi.gui.config.ConfigService
import com.github.hatoyuze.shiromi.gui.data.local.DatabaseCacheStorage
import com.github.hatoyuze.shiromi.protocol.api.LuoguApi
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mutex-guarded singleton provider for [LuoguApi].
 *
 * Rebuilds the instance when the configured cookie changes, registers cookie
 * refresh from [ConfigService] and warms up once per instance (failures are
 * ignored, matching the previous service behavior).
 */
class LuoguApiProvider(
    private val settings: ConfigService,
    private val cacheStorage: DatabaseCacheStorage?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    @Volatile
    private var instance: LuoguApi? = null

    suspend fun get(): LuoguApi = mutex.withLock {
        val current = instance
        val expectedCookie = settings.luoguCookie
        if (current != null && current.cookie == expectedCookie) return current

        val api = LuoguApi(cacheStorage).apply {
            cookie = expectedCookie
            settings.luoguUid.toIntOrNull()?.takeIf { it > 0 }?.let { uid0 = it }
            initCookieRefresh {
                settings.luoguCookie.takeIf { it.isNotBlank() }
            }
        }
        instance = api
        scope.launch {
            try {
                api.warmUp()
            } catch (_: Exception) {
                // non-critical
            }
        }
        api
    }
}
