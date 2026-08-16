package com.github.hatoyuze.luogu.skill.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GzipTest {

    @Test
    fun gzipRoundTrip_shouldPreservePayload() {
        val payload = "洛谷协议层 gzip 往返测试 — hello luogu!".encodeToByteArray()
        val compressed = compressGzip(payload)
        assertTrue(compressed.size > 10, "gzip output should contain header + payload")
        assertContentEquals(payload, decompressGzip(compressed))
    }

    @Test
    fun gzipRoundTrip_shouldHandleEmptyInput() {
        assertContentEquals(ByteArray(0), decompressGzip(compressGzip(ByteArray(0))))
    }

    @Test
    fun gzipRoundTrip_shouldHandleLargeInput() {
        val payload = buildString { repeat(5000) { append("A".encodeToByteArray().decodeToString()) } }
            .encodeToByteArray()
        assertContentEquals(payload, decompressGzip(compressGzip(payload)))
    }
}
