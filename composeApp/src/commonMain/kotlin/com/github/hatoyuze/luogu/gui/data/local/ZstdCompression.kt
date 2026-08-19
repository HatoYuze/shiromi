// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.local

import com.ensody.kompressor.core.transform
import com.ensody.kompressor.zstd.ZstdCompressor
import com.ensody.kompressor.zstd.ZstdDecompressor

/**
 * zstd compression utility — thread-safe via fresh instances per call.
 */
object ZstdCompression {
    /** Compress thinking content (level 3 — fast, good ratio). */
    fun compress(text: String): ByteArray =
        ZstdCompressor(compressionLevel = 3).transform(text.encodeToByteArray())

    /** Compress tool calls JSON (level 9 — smaller, slower). */
    fun compressToolCalls(text: String): ByteArray =
        ZstdCompressor(compressionLevel = 9).transform(text.encodeToByteArray())

    /** Compress raw bytes (level 3). Used for API cache blobs. */
    fun compressBytes(bytes: ByteArray): ByteArray =
        ZstdCompressor(compressionLevel = 3).transform(bytes)

    /** Decompress raw bytes. Used for API cache blobs. */
    fun decompressBytes(bytes: ByteArray): ByteArray =
        ZstdDecompressor().transform(bytes)

    fun decompress(bytes: ByteArray): String =
        ZstdDecompressor().transform(bytes).decodeToString()
}
