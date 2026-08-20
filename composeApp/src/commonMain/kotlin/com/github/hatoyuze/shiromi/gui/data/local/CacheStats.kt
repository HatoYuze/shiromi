// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.local

/**
 * Top-level cache statistics, safe for cross-module consumption
 * (DatabaseCacheStorage → LuoguCacheManager → ViewModel → UI).
 */
data class GlobalCacheStats(
    val totalCount: Long,
    val totalSizeBytes: Long,
)
