package com.github.hatoyuze.luogu.gui.data.local

import com.github.hatoyuze.luogu.gui.LuoguDatabase

expect suspend fun createDatabaseWrapper(): LuoguDatabase

class DatabaseWrapper(
    private val db: LuoguDatabase,
) {
    fun getDatabase(): LuoguDatabase = db
}
