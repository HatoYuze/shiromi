package com.github.hatoyuze.luogu.gui.platform

import android.content.Context

/**
 * Android 应用上下文持有器。
 *
 * 在 `MainActivity.onCreate` 最先赋值，供 `dataPath`/`cachePath` 等
 * 平台路径与数据库驱动解析使用。
 */
object AppContextHolder {
    lateinit var context: Context
}
