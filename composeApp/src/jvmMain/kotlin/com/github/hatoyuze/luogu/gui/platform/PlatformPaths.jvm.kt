package com.github.hatoyuze.luogu.gui.platform

import java.io.File
import okio.Path
import okio.Path.Companion.toPath

/** 桌面 JVM：沿用 `~/.luogu-gui`，与历史版本的数据目录保持一致。 */
actual val dataPath: Path =
    File(System.getProperty("user.home") ?: ".", ".luogu-gui").absolutePath.toPath()

/** 桌面 JVM：`~/.luogu-gui/cache`。 */
actual val cachePath: Path =
    (File(System.getProperty("user.home") ?: ".", ".luogu-gui").absolutePath + "/cache").toPath()
