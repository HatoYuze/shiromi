// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.hatoyuze.shiromi.gui.LuoguDatabase
import com.github.hatoyuze.shiromi.gui.platform.dataPath
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
