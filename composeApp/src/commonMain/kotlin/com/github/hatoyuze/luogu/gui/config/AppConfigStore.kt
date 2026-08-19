// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.config

/**
 * Persistence boundary for [GuiConfig]. Implemented on the JVM by
 * [com.github.hatoyuze.luogu.gui.config.GuiConfigLoader]; the UI and
 * commonMain code only ever depend on this interface.
 */
interface AppConfigStore {
    fun load(): GuiConfig
    fun save(config: GuiConfig)
}
