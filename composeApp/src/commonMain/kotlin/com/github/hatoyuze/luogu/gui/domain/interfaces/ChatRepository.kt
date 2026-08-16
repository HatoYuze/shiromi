package com.github.hatoyuze.luogu.gui.domain.interfaces

import com.github.hatoyuze.luogu.gui.domain.model.*
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

    // Daily Problem — agent context + cache
    fun getDailyProblem(dateDays: Long): Flow<DailyProblemResult?>
    suspend fun saveDailyProblem(dateDays: Long, result: DailyProblemResult)
    fun getDailyProblemContext(): Flow<List<DailyProblemMessage>>
    suspend fun insertDailyProblemMessage(role: String, content: String)
    suspend fun trimDailyProblemContext(maxMessages: Int)
}
