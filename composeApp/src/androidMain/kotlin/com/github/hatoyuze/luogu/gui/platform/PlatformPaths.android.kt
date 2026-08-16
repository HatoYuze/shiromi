package com.github.hatoyuze.luogu.gui.platform

import okio.Path
import okio.Path.Companion.toPath

/** Android：应用私有数据目录 `context.filesDir`。 */
actual val dataPath: Path
    get() = AppContextHolder.context.filesDir.absolutePath.toPath()

/** Android：系统可回收缓存目录 `context.cacheDir`。 */
actual val cachePath: Path
    get() = AppContextHolder.context.cacheDir.absolutePath.toPath()
