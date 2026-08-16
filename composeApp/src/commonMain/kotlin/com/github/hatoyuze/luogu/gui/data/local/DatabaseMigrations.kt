package com.github.hatoyuze.luogu.gui.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * 数据库 Schema 之后的渐进式迁移（纯 SQL，供三平台驱动共用）。
 *
 * `runCatching` 的语句是为幂等而设：列已存在/不存在时静默跳过。
 */
internal object DatabaseMigrations {

    fun migrate(driver: SqlDriver) {
        // Migration: add edited_message_id to ChatBranch (v2 schema)
        runCatching {
            driver.execute(-1, "ALTER TABLE ChatBranch ADD COLUMN edited_message_id TEXT", 0)
        }

        // Migration: remove legacy columns (safe to run even if columns don't exist)
        runCatching {
            driver.execute(-1, "ALTER TABLE ChatMessage DROP COLUMN edit_versions_json", 0)
        }
        runCatching {
            driver.execute(-1, "ALTER TABLE ChatMessage DROP COLUMN child_branch_ids_json", 0)
        }

        // Migration: ensure each existing session has a 'main' branch
        driver.execute(
            -1,
            """
            INSERT OR IGNORE INTO ChatBranch(id, sessionId, parent_branch_id, fork_message_id, created_at)
            SELECT 'main', id, NULL, NULL, createdAt FROM ChatSession
            """,
            0,
        )

        // Migration: TodoItem.dueAt (nullable epoch millis)
        runCatching {
            driver.execute(-1, "ALTER TABLE TodoItem ADD COLUMN dueAt INTEGER", 0)
        }

        // Migration: CalendarEventEntity time fields (all-day flag + minutes since midnight)
        runCatching {
            driver.execute(-1, "ALTER TABLE CalendarEventEntity ADD COLUMN all_day INTEGER NOT NULL DEFAULT 0", 0)
        }
        runCatching {
            driver.execute(-1, "ALTER TABLE CalendarEventEntity ADD COLUMN time_minutes INTEGER", 0)
        }
    }
}
