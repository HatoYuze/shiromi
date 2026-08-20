// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.di

import com.github.hatoyuze.shiromi.gui.config.AppConfigStore
import com.github.hatoyuze.shiromi.gui.config.ConfigService
import com.github.hatoyuze.shiromi.gui.data.Logger
import com.github.hatoyuze.shiromi.gui.data.LoggingHook
import com.github.hatoyuze.shiromi.gui.data.log.FileLogSink
import com.github.hatoyuze.shiromi.gui.data.log.LogCategory
import com.github.hatoyuze.shiromi.gui.data.log.LogLevel
import com.github.hatoyuze.shiromi.gui.data.log.platformLogTunables
import com.github.hatoyuze.shiromi.gui.data.log.platformLogsDirectoryPath
import com.github.hatoyuze.shiromi.gui.data.local.DatabaseWrapper
import com.github.hatoyuze.shiromi.gui.data.local.createDatabaseWrapper
import com.github.hatoyuze.shiromi.gui.platform.loadBundledCoachPrompt
import io.github.hatoyuze.deepseek.protocol.net.HttpHookRegistry
import kotlinx.coroutines.runBlocking

/**
 * One-time platform-neutral bootstrap: loads persisted config into [ConfigService]
 * (falling back to the bundled coach prompt when empty), initializes file logging,
 * opens the SQLite database and registers the HTTP logging hook.
 *
 * Called by each platform entry point (desktop `main`, Android `MainActivity`,
 * iOS `MainViewController`) before composition starts.
 */
class AppBootstrap(
    private val configStore: AppConfigStore,
) {
    /**
     * Placeholder written by a previous build's default template. Treated as
     * "not configured" so the bundled coach prompt from resources is used.
     */
    private val legacyCoachPlaceholder =
        "You are a patient OI coach. Guide the student with questions and hints instead of direct answers."

    fun initialize(): DatabaseWrapper {
        val config = configStore.load()
        ConfigService.apply(
            if (config.coachPrompt.isBlank() || config.coachPrompt == legacyCoachPlaceholder) {
                config.copy(coachPrompt = loadBundledCoachPrompt())
            } else {
                config
            },
        )
        initLogging()
        val wrapper = DatabaseWrapper(runBlocking { createDatabaseWrapper() })
        HttpHookRegistry.add(LoggingHook())
        Logger.info(LogCategory.APP, "app.start", "Shiromi starting")
        return wrapper
    }

    private fun initLogging() {
        val tunables = platformLogTunables()
        Logger.init(
            sink = FileLogSink(platformLogsDirectoryPath(), tunables.maxBytes),
            minLevel = LogLevel.INFO,
            captureAssistantMessages = tunables.captureAssistantMessages,
            maxBodyBytes = tunables.maxBodyBytes,
        )
    }
}
