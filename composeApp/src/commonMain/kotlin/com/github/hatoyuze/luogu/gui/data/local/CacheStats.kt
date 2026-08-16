package com.github.hatoyuze.luogu.gui.data.local

/**
 * Top-level cache statistics, safe for cross-module consumption
 * (DatabaseCacheStorage → LuoguCacheManager → ViewModel → UI).
 */
data class GlobalCacheStats(
    val totalCount: Long,
    val totalSizeBytes: Long,
)
