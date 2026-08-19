// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.platform

import okio.Path

/**
 * 应用用户数据目录（数据库、配置等持久化文件的根）。
 *
 * - 桌面 JVM: `~/.luogu-gui`（沿用既有路径，保证升级不丢数据）
 * - Android: `context.filesDir`
 * - iOS: `NSApplicationSupportDirectory` 下的应用子目录
 */
expect val dataPath: Path

/**
 * 应用缓存目录（可被系统随时清空的临时数据）。
 *
 * - 桌面 JVM: `~/.luogu-gui/cache`
 * - Android: `context.cacheDir`
 * - iOS: `NSCachesDirectory` 下的应用子目录
 */
expect val cachePath: Path

/** 配置文件目录（TOML 持久化位置），固定为 `dataPath/config`。 */
fun defaultConfigDir(): Path = dataPath / "config"
