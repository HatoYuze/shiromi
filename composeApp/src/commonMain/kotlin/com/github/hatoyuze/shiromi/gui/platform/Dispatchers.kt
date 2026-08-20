// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 阻塞 IO 专用调度器。
 *
 * - JVM（桌面/Android）：[kotlinx.coroutines.Dispatchers.IO]
 * - iOS：无专用 IO 调度器，使用 [kotlinx.coroutines.Dispatchers.Default]
 */
expect val ioDispatcher: CoroutineDispatcher
