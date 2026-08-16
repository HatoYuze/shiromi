package com.github.hatoyuze.luogu.gui.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.github.hatoyuze.luogu.gui.LuoguDatabase

actual suspend fun createDatabaseWrapper(): LuoguDatabase {
    // NativeSqliteDriver 构造时按需建表（框架链接需 -lsqlite3）
    val driver = NativeSqliteDriver(LuoguDatabase.Schema, "chat.db")
    DatabaseMigrations.migrate(driver)
    return LuoguDatabase(driver)
}
