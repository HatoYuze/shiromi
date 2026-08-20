// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS 平台目录解析：Application Support / Caches 下的 `shiromi` 子目录，
 * 首次访问时通过 NSFileManager 递归创建。
 */
@OptIn(ExperimentalForeignApi::class)
private fun nsAppDirectory(domain: ULong, subDir: String): Path {
    val paths = NSSearchPathForDirectoriesInDomains(domain, NSUserDomainMask.toULong(), true)
    val base = paths.firstOrNull() as? String ?: NSTemporaryDirectoryFallback
    val dir = "$base/$subDir"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir.toPath()
}

private const val NSTemporaryDirectoryFallback = "/tmp"

/** iOS：`~/Library/Application Support/shiromi`。 */
actual val dataPath: Path by lazy {
    nsAppDirectory(NSApplicationSupportDomainValue, "shiromi")
}

/** iOS：`~/Library/Caches/shiromi`。 */
actual val cachePath: Path by lazy {
    nsAppDirectory(NSCachesDomainValue, "shiromi")
}

private val NSApplicationSupportDomainValue = NSApplicationSupportDirectory.toULong()
private val NSCachesDomainValue = NSCachesDirectory.toULong()
