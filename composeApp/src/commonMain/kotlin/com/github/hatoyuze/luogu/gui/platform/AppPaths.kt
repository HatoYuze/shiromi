// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.platform

import okio.Path

/**
 * Returns a cache directory under [cachePath] (`cachePath/[name]`),
 * creating it if needed. Keeps cached artifacts out of the process working directory.
 */
fun appCacheDir(name: String): Path = cachePath / name
