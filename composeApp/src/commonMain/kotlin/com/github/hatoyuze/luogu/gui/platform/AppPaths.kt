package com.github.hatoyuze.luogu.gui.platform

import okio.Path

/**
 * Returns a cache directory under [cachePath] (`cachePath/[name]`),
 * creating it if needed. Keeps cached artifacts out of the process working directory.
 */
fun appCacheDir(name: String): Path = cachePath / name
