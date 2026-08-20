// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.local

import com.github.hatoyuze.shiromi.gui.LuoguDatabase

expect suspend fun createDatabaseWrapper(): LuoguDatabase

class DatabaseWrapper(
    private val db: LuoguDatabase,
) {
    fun getDatabase(): LuoguDatabase = db
}
