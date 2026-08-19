// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.log

import java.io.File

/**
 * Resolves the platform user-data directory for log files.
 *
 * - Windows: `%APPDATA%/LuoguHelper/logs`
 * - Linux: `$XDG_DATA_HOME` (fallback `~/.local/share`) + `luogu-gui/logs`
 * - macOS: `~/Library/Application Support/LuoguHelper/logs`
 *
 * `-Dluogu-gui.logs.dir` overrides the resolved directory.
 */
fun resolveLogsDirectory(
    osName: String = System.getProperty("os.name") ?: "",
    env: (String) -> String? = { System.getenv(it) },
    home: String = System.getProperty("user.home") ?: ".",
    overrideDir: String? = System.getProperty("luogu-gui.logs.dir"),
): String {
    overrideDir?.takeIf { it.isNotBlank() }?.let { return File(it).absolutePath }

    val os = osName.lowercase()
    val base = when {
        os.contains("win") -> {
            val appData = env("APPDATA") ?: File(home, "AppData/Roaming").path
            File(appData, "LuoguHelper")
        }
        os.contains("mac") -> File(home, "Library/Application Support/LuoguHelper")
        else -> {
            val dataHome = env("XDG_DATA_HOME") ?: File(home, ".local/share").path
            File(dataHome, "luogu-gui")
        }
    }
    return File(base, "logs").absolutePath
}
