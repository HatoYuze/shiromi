package com.github.hatoyuze.luogu.gui.di

import com.github.hatoyuze.luogu.gui.config.AppConfigStore
import com.github.hatoyuze.luogu.gui.config.ConfigService
import com.github.hatoyuze.luogu.gui.config.GuiConfigLoader
import com.github.hatoyuze.luogu.gui.data.local.DatabaseWrapper
import com.github.hatoyuze.luogu.gui.data.remote.DeepseekChatService
import com.github.hatoyuze.luogu.gui.data.remote.LuoguApiProvider
import com.github.hatoyuze.luogu.gui.domain.chat.ChatService
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
