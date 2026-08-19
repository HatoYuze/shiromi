// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.log

import com.github.hatoyuze.luogu.gui.data.Logger

/**
 * 桌面日志目录：沿用平台约定（Windows %APPDATA% / Linux XDG / macOS App Support），
 * 支持 `-Dluogu-gui.logs.dir` 覆盖。
 */
actual fun platformLogsDirectory(): String = resolveLogsDirectory()

/** 桌面日志参数：从 `-Dluogu-gui.logs.*` 系统属性读取，缺省用默认值。 */
actual fun platformLogTunables(): LogTunables {
    val maxBytes = System.getProperty("luogu-gui.logs.maxBytes")?.toLongOrNull()
        ?: FileLogSink.DEFAULT_MAX_BYTES
    val captureAssistant =
        System.getProperty("luogu-gui.logs.captureAssistantMessages")?.toBooleanStrictOrNull() ?: true
    val maxBodyBytes = System.getProperty("luogu-gui.logs.maxBodyBytes")?.toIntOrNull()
        ?: Logger.DEFAULT_MAX_BODY_BYTES
    return LogTunables(maxBytes, captureAssistant, maxBodyBytes)
}
