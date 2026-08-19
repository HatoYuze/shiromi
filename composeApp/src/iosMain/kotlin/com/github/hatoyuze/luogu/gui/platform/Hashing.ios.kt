// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256

@OptIn(ExperimentalForeignApi::class)
actual fun sha256Hex(input: String): String {
    val data = input.encodeToByteArray()
    val digest = UByteArray(DIGEST_LENGTH)
    data.usePinned { pinnedData ->
        digest.usePinned { pinnedDigest ->
            CC_SHA256(pinnedData.addressOf(0), data.size.toUInt(), pinnedDigest.addressOf(0))
        }
    }
    return digest.joinToString("") { it.toString(16).padStart(2, '0') }
}

private const val DIGEST_LENGTH = 32
