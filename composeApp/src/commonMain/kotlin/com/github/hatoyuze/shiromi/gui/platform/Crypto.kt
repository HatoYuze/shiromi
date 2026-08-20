// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 轻量配置混淆（XOR + Base64）。
 *
 * 仅用于防止明文 API key / cookie 直接出现在配置文件中，
 * 不是安全加密；密钥来源于各平台的设备指纹（见 [deviceKeySeed]）。
 */
object Crypto {

    private val key: ByteArray by lazy { deriveKey(deviceKeySeed()) }

    internal fun deriveKey(seed: String): ByteArray {
        val bytes = seed.encodeToByteArray()
        return when {
            bytes.isEmpty() -> "default-key-12345-for-luogu".encodeToByteArray()
            bytes.size <= 32 -> bytes
            else -> bytes.copyOf(32)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encrypt(plain: String): String {
        val data = plain.encodeToByteArray()
        val xored = xorWithKey(data)
        return Base64.Default.encode(xored)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decrypt(encoded: String): String = try {
        val data = Base64.Default.decode(encoded)
        xorWithKey(data).decodeToString()
    } catch (_: Exception) {
        encoded
    }

    private fun xorWithKey(data: ByteArray): ByteArray =
        ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
}

/**
 * 设备指纹种子，用于派生 [Crypto] 的 XOR 密钥。
 * 每个平台提供稳定且互不相同的实现。
 */
expect fun deviceKeySeed(): String
