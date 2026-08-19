// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.local

/**
 * Cache management service consumed by ViewModels and UI.
 * Wraps [DatabaseCacheStorage] management APIs and returns
 * top-level [GlobalCacheStats] / [CacheTypeStats] data classes.
 */
class LuoguCacheManager(
    private val cacheStorage: DatabaseCacheStorage,
) {
    suspend fun getGlobalStats(): GlobalCacheStats = cacheStorage.getGlobalStats()

    suspend fun clearAll() = cacheStorage.clearAll()

    /** Delete cached problem detail so next fetch re-hits the network. */
    suspend fun invalidateProblem(pid: String) {
        cacheStorage.delete("problem_${pid.uppercase()}")
    }
}
