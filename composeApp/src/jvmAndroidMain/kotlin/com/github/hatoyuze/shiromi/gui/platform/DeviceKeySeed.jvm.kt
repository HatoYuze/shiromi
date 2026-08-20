// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import java.net.NetworkInterface

/**
 * JVM/Android 设备指纹：优先使用网卡 MAC 地址，失败时退回 `user.name`。
 * 与历史版本的 Crypto 密钥派生保持一致（同一台机器上的密钥不变）。
 */
actual fun deviceKeySeed(): String = try {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .flatMap { it.hardwareAddress?.asSequence() ?: emptySequence() }
        .joinToString("-") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
} catch (_: Exception) {
    System.getProperty("user.name") ?: "luogu-gui"
}
