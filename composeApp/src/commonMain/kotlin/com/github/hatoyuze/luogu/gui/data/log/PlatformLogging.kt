// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.log

import okio.Path
import okio.Path.Companion.toPath

/**
 * 平台日志根目录（绝对路径字符串）。
 *
 * - 桌面 JVM：沿用既有平台约定（Windows `%APPDATA%` / Linux XDG / macOS
 *   Application Support），支持 `-Dluogu-gui.logs.dir` 覆盖，见 jvmMain 的
 *   [resolveLogsDirectory]
 * - Android / iOS：`dataPath/logs`
 */
expect fun platformLogsDirectory(): String

/** 平台日志参数（桌面读取 `-Dluogu-gui.logs.*` 系统属性，移动端用默认值）。 */
data class LogTunables(
    val maxBytes: Long,
    val captureAssistantMessages: Boolean,
    val maxBodyBytes: Int,
)

expect fun platformLogTunables(): LogTunables

/** [platformLogsDirectory] 的便捷 Path 形式。 */
fun platformLogsDirectoryPath(): Path = platformLogsDirectory().toPath()
