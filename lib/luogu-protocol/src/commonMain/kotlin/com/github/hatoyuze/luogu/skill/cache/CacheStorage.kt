// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.skill.cache

/**
 * Platform-abstracted key-value cache storage.
 * Used by [LuoguCache] to persist API responses.
 */
interface CacheStorage {
    suspend fun read(key: String): ByteArray?
    suspend fun write(key: String, data: ByteArray, ttlMs: Long? = null)
    suspend fun delete(key: String)
    suspend fun listEntries(): List<CacheEntry>
}

data class CacheEntry(
    val key: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
)
