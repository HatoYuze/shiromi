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
