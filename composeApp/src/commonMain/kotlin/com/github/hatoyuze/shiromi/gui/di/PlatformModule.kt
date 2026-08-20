// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.di

import com.github.hatoyuze.shiromi.gui.config.AppConfigStore
import com.github.hatoyuze.shiromi.gui.config.ConfigService
import com.github.hatoyuze.shiromi.gui.config.GuiConfigLoader
import com.github.hatoyuze.shiromi.gui.data.local.DatabaseWrapper
import com.github.hatoyuze.shiromi.gui.data.remote.DeepseekChatService
import com.github.hatoyuze.shiromi.gui.data.remote.LuoguApiProvider
import com.github.hatoyuze.shiromi.gui.domain.chat.ChatService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform-neutral Koin bindings over platform services.
 * [databaseWrapper] is created once by [AppBootstrap] during startup and reused here.
 */
fun platformModule(databaseWrapper: DatabaseWrapper): Module = module {
    single { databaseWrapper }
    single<AppConfigStore> { GuiConfigLoader() }
    single { ConfigService }
    single { LuoguApiProvider(get(), get()) }
    single<ChatService> { DeepseekChatService(get(), get(), get()) }
}
