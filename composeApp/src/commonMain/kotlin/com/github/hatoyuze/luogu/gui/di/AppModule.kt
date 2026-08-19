// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.di

import com.github.hatoyuze.luogu.gui.data.local.DatabaseCacheStorage
import com.github.hatoyuze.luogu.gui.data.local.DatabaseWrapper
import com.github.hatoyuze.luogu.gui.data.local.LuoguCacheManager
import com.github.hatoyuze.luogu.gui.data.local.SqlDelightMemoryProvider
import com.github.hatoyuze.luogu.gui.data.remote.DailyProblemAgent
import com.github.hatoyuze.luogu.gui.data.repository.ChatRepositoryImpl
import com.github.hatoyuze.luogu.gui.domain.interfaces.ChatRepository
import com.github.hatoyuze.luogu.gui.presentation.state.ChatViewModel
import com.github.hatoyuze.luogu.gui.presentation.state.HomeViewModel
import com.github.hatoyuze.luogu.skill.coach.MemoryProvider
import org.koin.dsl.module

/**
 * Platform-independent Koin module. Platform bindings (DatabaseWrapper,
 * AppConfigStore, ChatService) are provided by [jvmModule] on the JVM.
 */
fun commonModule() = module {
    single { DatabaseCacheStorage(get<DatabaseWrapper>().getDatabase()) }
    single { LuoguCacheManager(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get()) }
    single<MemoryProvider> { SqlDelightMemoryProvider(get<DatabaseWrapper>().getDatabase()) }
    single { DailyProblemAgent(get<DatabaseWrapper>().getDatabase(), get()) }
    single { ChatViewModel(get(), get()) }
    single { HomeViewModel(get(), get()) }
}
