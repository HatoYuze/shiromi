// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.local

import com.github.hatoyuze.shiromi.gui.LuoguDatabase
import com.github.hatoyuze.shiromi.protocol.cache.CacheEntry
import com.github.hatoyuze.shiromi.protocol.cache.CacheStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed [CacheStorage] implementation for Luogu API responses.
 *
 * Stores entries in the [ApiCacheEntry] table with zstd compression.
 * TTL is enforced at the SQL level via the [expire_at] column.
 * LRU eviction (max 10k entries / 100 MB) runs after each write,
 * wrapped in a transaction to avoid TOCTOU races.
 */
class DatabaseCacheStorage(
    private val db: LuoguDatabase,
) : CacheStorage {

    companion object {
        private const val MAX_ENTRIES = 10_000L
        private const val MAX_SIZE_BYTES = 100L * 1024 * 1024  // 100 MB
    }

    // ── CacheStorage ──

    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        val row = db.luoguDatabaseQueries.selectCacheEntry(key, now).executeAsOneOrNull()
            ?: return@withContext null

        db.luoguDatabaseQueries.updateAccessedAt(now, key)

        return@withContext try {
            ZstdCompression.decompressBytes(row.data_)
        } catch (_: Exception) {
            // Corrupted blob → delete and degrade to cache miss
            db.luoguDatabaseQueries.deleteByKey(key)
            null
        }
    }

    override suspend fun write(key: String, data: ByteArray, ttlMs: Long?) =
        withContext(Dispatchers.Default) {
            val compressed = ZstdCompression.compressBytes(data)
            val now = Clock.System.now().toEpochMilliseconds()
            val expireAt = when (ttlMs) {
                null, -1L -> null       // permanent
                else -> now + ttlMs
            }

            db.transaction {
                db.luoguDatabaseQueries.deleteByKey(key)
                db.luoguDatabaseQueries.insertCache(
                    cache_key = key,
                    data_ = compressed,
                    size_bytes = compressed.size.toLong(),
                    content_type = key.substringBefore('_'),
                    created_at = now,
                    accessed_at = now,
                    expire_at = expireAt,
                )
            }
            enforceLimits()
        }

    override suspend fun delete(key: String) = withContext(Dispatchers.Default) {
        db.luoguDatabaseQueries.deleteByKey(key)
    }

    override suspend fun listEntries(): List<CacheEntry> = withContext(Dispatchers.Default) {
        // Not meaningful for DB — return empty; stats come from getGlobalStats/getStatsByType
        emptyList()
    }

    // ── Management API ──

    suspend fun clearAll() = withContext(Dispatchers.Default) {
        db.luoguDatabaseQueries.deleteAll()
    }

    suspend fun getGlobalStats(): GlobalCacheStats = withContext(Dispatchers.Default) {
        val count = db.luoguDatabaseQueries.countAll().executeAsOne()
        val size = db.luoguDatabaseQueries.sumAllSize().executeAsOne()
        GlobalCacheStats(totalCount = count, totalSizeBytes = size)
    }

    // ── LRU eviction ──

    private suspend fun enforceLimits() {
        db.transaction {
            val count = db.luoguDatabaseQueries.countAll().executeAsOne()
            if (count > MAX_ENTRIES) {
                db.luoguDatabaseQueries.deleteOverflow(count - MAX_ENTRIES)
            }
            // Volume-based: delete oldest until under limit
            while (true) {
                val size = db.luoguDatabaseQueries.sumAllSize().executeAsOne()
                if (size <= MAX_SIZE_BYTES) break
                val oldest = db.luoguDatabaseQueries.selectOldestCacheKey().executeAsOneOrNull()
                    ?: break
                db.luoguDatabaseQueries.deleteByKey(oldest)
            }
        }
    }
}
