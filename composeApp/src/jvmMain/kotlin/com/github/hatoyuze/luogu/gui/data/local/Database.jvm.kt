package com.github.hatoyuze.luogu.gui.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.hatoyuze.luogu.gui.LuoguDatabase
import com.github.hatoyuze.luogu.gui.platform.dataPath
import java.io.File

actual suspend fun createDatabaseWrapper(): LuoguDatabase {
    val dbDir = File(dataPath.toString())
    dbDir.mkdirs()
    val dbPath = File(dbDir, "chat.db").absolutePath
    val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
    LuoguDatabase.Schema.create(driver)
    DatabaseMigrations.migrate(driver)
    return LuoguDatabase(driver)
}
