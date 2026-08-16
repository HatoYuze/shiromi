package com.github.hatoyuze.luogu.gui.data.repository

import com.github.hatoyuze.luogu.gui.ChatMessage
import com.github.hatoyuze.luogu.gui.ChatSession
import com.github.hatoyuze.luogu.gui.CalendarEventEntity
import com.github.hatoyuze.luogu.gui.ChatBranch
import com.github.hatoyuze.luogu.gui.StudyTopicEntity
import com.github.hatoyuze.luogu.gui.LuoguDatabase
import com.github.hatoyuze.luogu.gui.TodoItem
import com.github.hatoyuze.luogu.gui.data.local.DatabaseWrapper
import com.github.hatoyuze.luogu.gui.data.local.ZstdCompression
import com.github.hatoyuze.luogu.gui.domain.interfaces.ChatRepository
import com.github.hatoyuze.luogu.gui.domain.model.*
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.github.hatoyuze.luogu.gui.DailyProblemContext
import com.github.hatoyuze.luogu.gui.DailyProblemCache
import com.github.hatoyuze.luogu.gui.platform.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer

class ChatRepositoryImpl(
    databaseWrapper: DatabaseWrapper,
) : ChatRepository {

    private val db: LuoguDatabase = databaseWrapper.getDatabase()

    private val queries get() = db.luoguDatabaseQueries

    // ── Sessions ──

    override fun getAllSessions(): Flow<List<ChatSessionDomainModel>> {
        return queries.selectAllSessions()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomainModel() } }
    }

    override fun getSessionsByType(type: SessionType): Flow<List<ChatSessionDomainModel>> {
        val typeStr = type.name.lowercase()
        return queries.selectSessionsByType(typeStr)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomainModel() } }
    }

    override suspend fun createSession(session: ChatSessionDomainModel) {
        withContext(Dispatchers.Default) {
            queries.insertSession(
                id = session.id,
                title = session.title,
                type = session.type.name.lowercase(),
                createdAt = session.createdAt,
                lastModified = session.lastModified,
                last_active_branch_id = session.lastActiveBranchId,
            )
        }
    }

    override suspend fun updateSession(session: ChatSessionDomainModel) {
        withContext(Dispatchers.Default) {
            queries.updateSession(
                title = session.title,
                lastModified = session.lastModified,
                last_active_branch_id = session.lastActiveBranchId,
                id = session.id,
            )
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.Default) {
            queries.deleteSession(sessionId)
        }
    }

    // ── Messages ──

    override fun getMessages(sessionId: String): Flow<List<ChatMessageDomainModel>> {
        return queries.selectMessagesBySession(sessionId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomainModel() } }
    }

    override suspend fun insertMessage(message: ChatMessageDomainModel) {
        withContext(Dispatchers.Default) {
            queries.insertMessage(
                id = message.id,
                sessionId = message.sessionId,
                content = message.content,
                thinkingContent = message.thinkingContent?.let { ZstdCompression.compress(it) },
                toolCalls = serializeToolCalls(message.toolCalls),
                segments = serializeSegments(message.segments),
                thinkingElapsedSec = message.thinkingElapsedSec?.toLong(),
                totalTokens = message.totalTokens,
                finishReason = message.finishReason,
                isUser = if (message.isUser) 1L else 0L,
                status = message.status.name,
                timestamp = message.timestamp,
                branch_id = message.branchId,
                parent_message_id = message.parentMessageId,
                deepseek_message_index = message.deepseekMessageIndex?.toLong(),
            )
        }
    }

    override suspend fun updateMessage(message: ChatMessageDomainModel) {
        withContext(Dispatchers.Default) {
            queries.updateMessage(
                content = message.content,
                thinkingContent = message.thinkingContent?.let { ZstdCompression.compress(it) },
                toolCalls = serializeToolCalls(message.toolCalls),
                segments = serializeSegments(message.segments),
                thinkingElapsedSec = message.thinkingElapsedSec?.toLong(),
                totalTokens = message.totalTokens,
                finishReason = message.finishReason,
                status = message.status.name,
                branch_id = message.branchId,
                parent_message_id = message.parentMessageId,
                deepseek_message_index = message.deepseekMessageIndex?.toLong(),
                id = message.id,
            )
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        withContext(Dispatchers.Default) {
            queries.deleteMessage(messageId)
        }
    }

    override suspend fun deleteMessages(sessionId: String) {
        withContext(Dispatchers.Default) {
            queries.deleteMessagesBySession(sessionId)
        }
    }

    // ── Fork / branch queries ──

    override suspend fun getMessagesByBranch(sessionId: String, branchId: String): List<ChatMessageDomainModel> {
        return withContext(Dispatchers.Default) {
            queries.getMessagesByBranch(sessionId, branchId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun insertBranch(branch: ChatBranchDomainModel) {
        withContext(Dispatchers.Default) {
            queries.insertBranch(
                id = branch.id,
                sessionId = branch.sessionId,
                parent_branch_id = branch.parentBranchId,
                fork_message_id = branch.forkMessageId,
                edited_message_id = branch.editedMessageId,
                created_at = branch.createdAt,
            )
        }
    }

    override suspend fun getBranchesBySession(sessionId: String): List<ChatBranchDomainModel> {
        return withContext(Dispatchers.Default) {
            queries.getBranchesBySession(sessionId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun getBranchesByForkMessage(forkMessageId: String): List<ChatBranchDomainModel> {
        return withContext(Dispatchers.Default) {
            queries.getBranchesByForkMessage(forkMessageId).executeAsList().map { it.toDomainModel() }
        }
    }

    override suspend fun deleteBranchesBySession(sessionId: String) {
        withContext(Dispatchers.Default) {
            queries.deleteBranchesBySession(sessionId)
        }
    }

    // ── Todos ──

    override fun getAllTodos(): Flow<List<TodoItemDomainModel>> {
        return queries.selectAllTodos()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomainModel() } }
    }

    override suspend fun insertTodo(todo: TodoItemDomainModel) {
        withContext(Dispatchers.Default) {
            queries.insertTodo(
                id = todo.id,
                title = todo.title,
                completed = if (todo.completed) 1L else 0L,
                createdAt = todo.createdAt,
            )
        }
    }

    override suspend fun updateTodoCompleted(id: String, completed: Boolean) {
        withContext(Dispatchers.Default) {
            queries.updateTodoCompleted(
                completed = if (completed) 1L else 0L,
                id = id,
            )
        }
    }

    override suspend fun deleteTodo(id: String) {
        withContext(Dispatchers.Default) {
            queries.deleteTodo(id)
        }
    }

    // ── Coach ──

    override suspend fun insertRecommendation(sessionId: String, pid: String) {
        withContext(Dispatchers.Default) {
            queries.insertRecommendation(
                sessionId = sessionId,
                pid = pid,
                createdAt = com.github.hatoyuze.luogu.gui.platform.currentTimeMillis(),
            )
        }
    }

    override fun getAllRecommendations(): Flow<List<String>> {
        return queries.selectAllRecommendations()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    // ── Calendar Events ──

    override fun getActiveEvents(fromDays: Long): Flow<List<CalendarEvent>> {
        return queries.selectActiveEvents(fromDays)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun insertCalendarEvent(event: CalendarEvent) {
        withContext(Dispatchers.Default) {
            queries.insertCalendarEvent(
                id = event.id,
                name = event.name,
                date_epoch_days = event.date.toEpochDays(),
                created_at_ms = event.createdAtMs,
                color = event.color.toLong(),
                pinned = if (event.pinned) 1L else 0L,
            )
        }
    }

    override suspend fun deleteCalendarEvent(eventId: String) {
        withContext(Dispatchers.Default) {
            queries.deleteCalendarEvent(eventId)
        }
    }

    override suspend fun updateCalendarEventPin(eventId: String, pinned: Boolean) {
        withContext(Dispatchers.Default) {
            queries.updateCalendarEventPin(
                pinned = if (pinned) 1L else 0L,
                id = eventId,
            )
        }
    }

    // ── Study Topic ──

    override fun getStudyTopic(): Flow<StudyTopic> {
        return queries.selectStudyTopic()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.firstOrNull()?.toDomain() ?: StudyTopic() }
    }

    override suspend fun saveStudyTopic(topic: StudyTopic) {
        withContext(Dispatchers.Default) {
            queries.insertOrReplaceStudyTopic(
                name = topic.name,
                current_count = topic.currentCount.toLong(),
                goal_count = topic.goalCount.toLong(),
            )
        }
    }

    // ── Daily Problem ──

    override fun getDailyProblem(dateDays: Long): Flow<DailyProblemResult?> {
        return queries.selectDailyProblem(dateDays)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.firstOrNull()?.toDailyProblemResult() }
    }

    override suspend fun saveDailyProblem(dateDays: Long, result: DailyProblemResult) {
        withContext(Dispatchers.Default) {
            queries.insertOrReplaceDailyProblem(
                date_epoch_days = dateDays,
                pid = result.pid,
                reason = result.reason,
                tips = tipsJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer<String>()), result.tips),
                created_at_ms = currentTimeMillis(),
            )
        }
    }

    override fun getDailyProblemContext(): Flow<List<DailyProblemMessage>> {
        return queries.selectDailyProblemContext()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDailyProblemMessage() } }
    }

    override suspend fun insertDailyProblemMessage(role: String, content: String) {
        withContext(Dispatchers.Default) {
            queries.insertDailyProblemMessage(
                role = role,
                content = content,
                created_at_ms = currentTimeMillis(),
            )
        }
    }

    override suspend fun trimDailyProblemContext(maxMessages: Int) {
        withContext(Dispatchers.Default) {
            val count = queries.selectDailyProblemContextCount().executeAsOne()
            if (count > maxMessages) {
                queries.deleteOldestContextMessages(count - maxMessages)
            }
        }
    }

    companion object {
        internal val tipsJson = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true; isLenient = true
        }
    }
}

// ── Type-safe mapping extensions ──

private fun ChatSession.toDomainModel(): ChatSessionDomainModel = ChatSessionDomainModel(
    id = id,
    title = title,
    type = SessionType.fromString(type),
    createdAt = createdAt,
    lastModified = lastModified,
    lastActiveBranchId = last_active_branch_id,
)

private fun ChatMessage.toDomainModel(): ChatMessageDomainModel = ChatMessageDomainModel(
    id = id,
    sessionId = sessionId,
    content = content,
    isUser = isUser == 1L,
    status = try { MessageStatus.valueOf(status) } catch (_: Exception) { MessageStatus.SENT },
    timestamp = timestamp,
    thinkingContent = thinkingContent?.let { ZstdCompression.decompress(it) },
    toolCalls = deserializeToolCalls(toolCalls),  // ByteArray? from BLOB, decompressed
    segments = deserializeSegments(segments),
    thinkingElapsedSec = thinkingElapsedSec?.toInt(),
    totalTokens = totalTokens,
    finishReason = finishReason,
    branchId = branch_id,
    parentMessageId = parent_message_id,
    deepseekMessageIndex = deepseek_message_index?.toInt(),
)

private fun ChatBranch.toDomainModel(): ChatBranchDomainModel = ChatBranchDomainModel(
    id = id,
    sessionId = sessionId,
    parentBranchId = parent_branch_id,
    forkMessageId = fork_message_id,
    editedMessageId = edited_message_id,
    createdAt = created_at,
)

private val toolCallsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; classDiscriminator = "segType" }

private fun serializeToolCalls(toolCalls: List<com.github.hatoyuze.luogu.gui.domain.model.ToolCallInfo>?): ByteArray? {
    if (toolCalls.isNullOrEmpty()) return null
    return try {
        val json = toolCallsJson.encodeToString(kotlinx.serialization.serializer(), toolCalls)
        ZstdCompression.compressToolCalls(json)
    } catch (_: Exception) { null }
}

private fun deserializeToolCalls(raw: ByteArray?): List<com.github.hatoyuze.luogu.gui.domain.model.ToolCallInfo>? {
    if (raw == null) return null
    return try {
        val json = ZstdCompression.decompress(raw)
        toolCallsJson.decodeFromString(kotlinx.serialization.serializer(), json)
    } catch (_: Exception) { null }
}

private fun serializeSegments(segments: List<com.github.hatoyuze.luogu.gui.domain.model.MessageSegment>?): ByteArray? {
    if (segments.isNullOrEmpty()) return null
    return try {
        val json = toolCallsJson.encodeToString(kotlinx.serialization.serializer(), segments)
        ZstdCompression.compressToolCalls(json)  // level 9 — same as toolCalls
    } catch (_: Exception) { null }
}

private fun deserializeSegments(raw: ByteArray?): List<com.github.hatoyuze.luogu.gui.domain.model.MessageSegment>? {
    if (raw == null) return null
    return try {
        val json = ZstdCompression.decompress(raw)
        toolCallsJson.decodeFromString(kotlinx.serialization.serializer(), json)
    } catch (_: Exception) { null }
}

private fun TodoItem.toDomainModel(): TodoItemDomainModel = TodoItemDomainModel(
    id = id,
    title = title,
    completed = completed == 1L,
    createdAt = createdAt,
)

// ── CalendarEventEntity <-> CalendarEvent ──

private fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
    id = id,
    name = name,
    date = kotlinx.datetime.LocalDate.fromEpochDays(date_epoch_days),
    createdAtMs = created_at_ms,
    color = color.toInt(),
    pinned = pinned == 1L,
)

// ── StudyTopicEntity <-> StudyTopic ──

private fun StudyTopicEntity.toDomain(): StudyTopic = StudyTopic(
    name = name,
    currentCount = current_count.toInt(),
    goalCount = goal_count.toInt(),
)

// ── DailyProblemCache -> DailyProblemResult ──

private fun DailyProblemCache.toDailyProblemResult(): DailyProblemResult {
    val tips: List<String> = try {
        ChatRepositoryImpl.tipsJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(serializer<String>()),
            tips
        )
    } catch (_: Exception) { emptyList() }
    return DailyProblemResult(
        pid = pid,
        reason = reason,
        tips = tips,
    )
}

// ── DailyProblemContext -> DailyProblemMessage ──

private fun DailyProblemContext.toDailyProblemMessage(): DailyProblemMessage = DailyProblemMessage(
    role = role,
    content = content,
    createdAtMs = created_at_ms,
)
