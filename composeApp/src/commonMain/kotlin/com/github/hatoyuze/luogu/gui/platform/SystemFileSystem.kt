// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.platform

import okio.FileSystem

/**
 * The platform's default [FileSystem].
 *
 * `okio.FileSystem.SYSTEM` is only declared in okio's platform source sets
 * (jvm/android + native), not in its common metadata, so it cannot be referenced
 * from commonMain directly. This expect/actual exposes it to shared code.
 */
expect val systemFileSystem: FileSystem
