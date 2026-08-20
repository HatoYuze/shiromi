// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.domain.interfaces

import com.github.hatoyuze.shiromi.gui.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // Sessions
    fun getAllSessions(): Flow<List<ChatSessionDomainModel>>
    fun getSessionsByType(type: SessionType): Flow<List<ChatSessionDomainModel>>
    suspend fun createSession(session: ChatSessionDomainModel)
    suspend fun updateSession(session: ChatSessionDomainModel)
    suspend fun deleteSession(sessionId: String)

    // Messages
    fun getMessages(sessionId: String): Flow<List<ChatMessageDomainModel>>
    suspend fun insertMessage(message: ChatMessageDomainModel)
    suspend fun updateMessage(message: ChatMessageDomainModel)
    suspend fun deleteMessage(messageId: String)
    suspend fun deleteMessages(sessionId: String)

    /** Reads a single message by id; null when it does not exist (e.g. after deletion). */
    suspend fun getMessage(messageId: String): ChatMessageDomainModel?

    // Fork / branch queries
    suspend fun getMessagesByBranch(sessionId: String, branchId: String): List<ChatMessageDomainModel>
    suspend fun insertBranch(branch: ChatBranchDomainModel)
    suspend fun getBranchesBySession(sessionId: String): List<ChatBranchDomainModel>
    suspend fun getBranchesByForkMessage(forkMessageId: String): List<ChatBranchDomainModel>
    suspend fun deleteBranchesBySession(sessionId: String)

    // Todos
    fun getAllTodos(): Flow<List<TodoItemDomainModel>>
    suspend fun insertTodo(todo: TodoItemDomainModel)
    suspend fun updateTodoCompleted(id: String, completed: Boolean)
    suspend fun deleteTodo(id: String)

    // Coach — recommendations
    suspend fun insertRecommendation(sessionId: String, pid: String)
    fun getAllRecommendations(): Flow<List<String>>

    // Calendar events — all non-expired (date >= today)
    fun getActiveEvents(fromDays: Long): Flow<List<CalendarEvent>>
    suspend fun insertCalendarEvent(event: CalendarEvent)
    suspend fun deleteCalendarEvent(eventId: String)
    suspend fun updateCalendarEventPin(eventId: String, pinned: Boolean)

    // Study topic
    fun getStudyTopic(): Flow<StudyTopic>
    suspend fun saveStudyTopic(topic: StudyTopic)

    // Solved-problem stats (from CompletedProblem)
    /** CompletedProblem 表变化信号（SQLDelight 表级失效），用于驱动统计重算。 */
    fun getCompletedProblemChanges(): Flow<Unit>
    suspend fun countAllCompletedProblems(): Long
    suspend fun countCompletedProblemsSince(fromEpochMillis: Long): Long

    // Daily Problem — agent context + cache
    fun getDailyProblem(dateDays: Long): Flow<DailyProblemResult?>
    suspend fun saveDailyProblem(dateDays: Long, result: DailyProblemResult)
    fun getDailyProblemContext(): Flow<List<DailyProblemMessage>>
    suspend fun insertDailyProblemMessage(role: String, content: String)
    suspend fun trimDailyProblemContext(maxMessages: Int)
}
