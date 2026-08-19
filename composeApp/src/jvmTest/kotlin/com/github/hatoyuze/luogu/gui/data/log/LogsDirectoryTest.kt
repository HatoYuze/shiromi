// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.log

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class LogsDirectoryTest {

    @Test
    fun windows_shouldUseAppData() {
        val result = resolveLogsDirectory(
            osName = "Windows 11",
            env = { if (it == "APPDATA") "C:\\Users\\me\\AppData\\Roaming" else null },
            home = "C:\\Users\\me",
            overrideDir = null,
        )
        // Built via File joins so the expectation is separator-agnostic on non-Windows JVMs
        assertEquals(File(File("C:\\Users\\me\\AppData\\Roaming", "LuoguHelper"), "logs").absolutePath, result)
    }

    @Test
    fun linux_shouldUseXdgDataHome() {
        val result = resolveLogsDirectory(
            osName = "Linux",
            env = { if (it == "XDG_DATA_HOME") "/home/me/.local/share" else null },
            home = "/home/me",
            overrideDir = null,
        )
        assertEquals(File("/home/me/.local/share/luogu-gui/logs").absolutePath, result)
    }

    @Test
    fun linux_withoutXdg_shouldFallbackToDotLocalShare() {
        val result = resolveLogsDirectory(
            osName = "Linux",
            env = { null },
            home = "/home/me",
            overrideDir = null,
        )
        assertEquals(File("/home/me/.local/share/luogu-gui/logs").absolutePath, result)
    }

    @Test
    fun mac_shouldUseApplicationSupport() {
        val result = resolveLogsDirectory(
            osName = "Mac OS X",
            env = { null },
            home = "/Users/me",
            overrideDir = null,
        )
        assertEquals(File("/Users/me/Library/Application Support/LuoguHelper/logs").absolutePath, result)
    }

    @Test
    fun overrideDir_shouldTakePrecedence() {
        val result = resolveLogsDirectory(
            osName = "Windows 11",
            env = { null },
            home = "C:\\Users\\me",
            overrideDir = "D:\\custom\\logs",
        )
        assertEquals(File("D:\\custom\\logs").absolutePath, result)
    }
}
