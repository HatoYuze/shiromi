// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.local

import com.github.hatoyuze.luogu.gui.LuoguDatabase
import com.github.hatoyuze.luogu.skill.coach.LevelHistoryEntry
import com.github.hatoyuze.luogu.skill.coach.MemoryProvider
import com.github.hatoyuze.luogu.skill.coach.StudentMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * [MemoryProvider] backed by SQLDelight.
 *
 * Read methods ([getMemory], [getSelectedProblems]) serve the coach tools
 * (`get_memory` / `get_selected`). Write methods persist results after the
 * coach session finishes (called by [ChatViewModel] on [CoachFinished]).
 */
class SqlDelightMemoryProvider(
    private val db: LuoguDatabase,
) : MemoryProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── MemoryProvider: read (for coach tools) ──

    override suspend fun getMemory(): StudentMemory? = withContext(Dispatchers.Default) {
        val row = db.luoguDatabaseQueries.selectMemory("default").executeAsOneOrNull()
            ?: return@withContext null

        val history = db.luoguDatabaseQueries.selectLevelHistory("default").executeAsList()
            .map { LevelHistoryEntry(topic = it.topic, score = it.score.toInt(), date = it.date, problem = it.problem) }

        StudentMemory(
            studentId = row.studentId,
            lastTopic = row.lastTopic,
            lastProblem = row.lastProblem,
            levelScore = row.levelScore.toInt(),
            levelHistory = history,
            painPoints = parseJsonArray(row.painPoints),
            progressPoints = parseJsonArray(row.progressPoints),
        )
    }

    override suspend fun getSelectedProblems(): List<String> = withContext(Dispatchers.Default) {
        db.luoguDatabaseQueries.selectAllCompletedProblems().executeAsList()
    }

    // ── Write: persist after coaching finishes ──

    /**
     * Save or update the student's memory from a coach [summary].
     *
     * @param lastTopic   the topic just covered
     * @param lastProblem the problem just solved (pid)
     * @param levelScore  updated level score (0–100)
     * @param painPoints  updated pain points
     * @param progressPoints updated progress points
     * @param historyDate date string for the level history entry
     */
    suspend fun saveMemory(
        lastTopic: String? = null,
        lastProblem: String? = null,
        levelScore: Int,
        painPoints: List<String> = emptyList(),
        progressPoints: List<String> = emptyList(),
        historyDate: String? = null,
        historyProblem: String? = null,
    ) = withContext(Dispatchers.Default) {
        val now = currentTime()
        val queries = db.luoguDatabaseQueries

        queries.insertOrReplaceMemory(
            studentId = "default",
            lastTopic = lastTopic,
            lastProblem = lastProblem,
            levelScore = levelScore.toLong(),
            painPoints = toJsonArray(painPoints),
            progressPoints = toJsonArray(progressPoints),
            updatedAt = now,
        )

        if (lastTopic != null && historyDate != null) {
            queries.insertLevelHistory(
                studentId = "default",
                topic = lastTopic,
                score = levelScore.toLong(),
                date = historyDate,
                problem = historyProblem,
            )
        }
    }

    suspend fun addCompletedProblem(pid: String) = withContext(Dispatchers.Default) {
        db.luoguDatabaseQueries.insertCompletedProblem(
            pid = pid,
            completedAt = currentTime(),
        )
    }

    suspend fun saveRecommendations(sessionId: String, pids: List<String>) = withContext(Dispatchers.Default) {
        val now = currentTime()
        val queries = db.luoguDatabaseQueries
        for (pid in pids) {
            queries.insertRecommendation(sessionId = sessionId, pid = pid, createdAt = now)
        }
    }

    suspend fun getAllRecommendations(): List<String> = withContext(Dispatchers.Default) {
        db.luoguDatabaseQueries.selectAllRecommendations().executeAsList()
    }

    // ── helpers ──

    private fun parseJsonArray(raw: String): List<String> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return try {
            json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }
        } catch (_: Exception) { emptyList() }
    }

    private fun toJsonArray(items: List<String>): String {
        if (items.isEmpty()) return "[]"
        return kotlinx.serialization.json.buildJsonArray {
            items.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
        }.toString()
    }

    private fun currentTime(): Long = com.github.hatoyuze.luogu.gui.platform.currentTimeMillis()
}
