// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import okio.FileSystem

/** JVM/Android: okio's default host filesystem (java.io-backed). */
actual val systemFileSystem: FileSystem = FileSystem.SYSTEM
