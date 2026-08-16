package com.github.hatoyuze.luogu.gui.data.local

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.github.hatoyuze.luogu.gui.LuoguDatabase
import com.github.hatoyuze.luogu.gui.platform.AppContextHolder

actual suspend fun createDatabaseWrapper(): LuoguDatabase {
    // name 交给 SQLiteOpenHelper，落在标准 databases 目录
    val driver = AndroidSqliteDriver(LuoguDatabase.Schema, AppContextHolder.context, "chat.db")
    DatabaseMigrations.migrate(driver)
    return LuoguDatabase(driver)
}
