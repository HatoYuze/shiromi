package com.github.hatoyuze.luogu.gui.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.hatoyuze.luogu.gui.config.ConfigService
import com.github.hatoyuze.luogu.gui.data.Logger
import com.github.hatoyuze.luogu.gui.data.log.LogCategory
import com.github.hatoyuze.luogu.gui.domain.chat.ChatService
import com.github.hatoyuze.luogu.gui.domain.chat.ChatSession
import com.github.hatoyuze.luogu.gui.domain.chat.parseAskUserArgs
import com.github.hatoyuze.luogu.gui.domain.interfaces.ChatRepository
import com.github.hatoyuze.luogu.gui.domain.model.*
import com.github.hatoyuze.luogu.gui.presentation.utils.ChatJobManager
import com.github.hatoyuze.luogu.gui.platform.currentTimeMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import com.github.hatoyuze.luogu.gui.platform.ioDispatcher

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatService: ChatService,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val jobManager = ChatJobManager()
    private var sessionsJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        observeUserQuestions()
        observeSessions()
        observeTodos()
        loadModels()
    }

    private fun observeTodos() {
        viewModelScope.launch {
            repository.getAllTodos().collect { todos ->
                _state.update { it.copy(todos = todos.filter { !it.completed }.take(3)) }
            }
        }
    }

    /** Drives the askuser card from service-emitted [com.github.hatoyuze.luogu.gui.domain.chat.UserQuestion]s. */
    private fun observeUserQuestions() {
        viewModelScope.launch {
            chatService.userQuestionEvents.collect { question ->
                _state.update {
                    it.copy(
                        pendingAskUser = PendingAskUser(
                            questionId = question.questionId,
                            desc = question.desc,
                            timeoutMs = question.timeoutMs,
                            isMulti = question.isMulti,
                            allowCustom = question.allowCustom,
                            options = question.options,
                            startedAtMs = question.startedAtMs,
                        ),
                    )
                }
            }
        }
    }

    fun handleEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.SendMessage -> sendMessage(event.content)
            is ChatEvent.SelectSession -> selectSession(event.session)
            is ChatEvent.SelectModel -> selectModel(event.model)
            is ChatEvent.CreateNewSession -> createNewSession(event.type)
            is ChatEvent.RetryMessage -> retryMessage(event.message)
            is ChatEvent.ClearError -> _state.update { it.copy(error = null) }
            is ChatEvent.DismissModelList -> _state.update { it.copy(showModelList = false) }
            is ChatEvent.ShowModelList -> _state.update { it.copy(showModelList = true) }
            is ChatEvent.GoHome -> goHome()
            is ChatEvent.AnswerAskUser -> submitAskUserAnswer(event.selectedOptions, event.customText)
            // ── New message actions ──
            is ChatEvent.StopGeneration -> stopGeneration()
            is ChatEvent.StartEdit -> {
                // Toggle: if already editing this message, cancel edit
                if (_state.value.editingMessageId == event.messageId) {
                    _state.update { it.copy(editingMessageId = null) }
                } else {
                    val msg = _state.value.messages.find { it.id == event.messageId }
                    if (msg != null && msg.isUser) {
                        _state.update { it.copy(editingMessageId = event.messageId) }
                    }
                }
            }
            is ChatEvent.CancelEdit -> _state.update { it.copy(editingMessageId = null) }
            is ChatEvent.SendEdit -> sendEdit(event.messageId, event.newContent)
            is ChatEvent.RequestDelete -> _state.update { it.copy(pendingDelete = PendingDelete(event.userMessageId, event.assistantMessageId)) }
            is ChatEvent.DismissDelete -> _state.update { it.copy(pendingDelete = null) }
            is ChatEvent.DeleteExchange -> {
                _state.update { it.copy(pendingDelete = null) }
                deleteExchange(event.userMessageId, event.assistantMessageId)
            }
            is ChatEvent.RegenerateMessage -> regenerateMessage(event.messageId)
            is ChatEvent.SwitchBranch -> viewModelScope.launch { switchToBranch(event.branchId) }
            is ChatEvent.ShowToast -> {
                _state.update { it.copy(toast = event.message) }
            }
            is ChatEvent.ClearToast -> {
                _state.update { it.copy(toast = null) }
            }
            else -> {}
        }
    }

    private fun sendMessage(content: String, parentMessageId: String? = null) {
        val sessionType = _state.value.sessionType
        viewModelScope.launch {
            var session = _state.value.currentSession
            if (session == null) {
                val id = generateId()
                val now = currentTime()
                session = ChatSessionDomainModel(
                    id = id, title = content.take(50), type = sessionType,
                    createdAt = now, lastModified = now,
                )
                repository.createSession(session)
                // Create default 'main' branch for new sessions
                repository.insertBranch(ChatBranchDomainModel(
                    id = "main", sessionId = id, createdAt = now,
                ))
                _state.update { it.copy(currentSession = session, showHomeScreen = false, activeBranchId = "main") }
        Logger.info(LogCategory.CHAT, "chat.sessionCreated", "session=${session.id} type=${sessionType}")
            }

            val branchId = _state.value.activeBranchId
            val userMsg = ChatMessageDomainModel(
                id = generateId(), sessionId = session.id, content = content,
                isUser = true, status = MessageStatus.SENT, timestamp = currentTime(),
                branchId = branchId, parentMessageId = parentMessageId,
            )
            repository.insertMessage(userMsg)
            val msgs = _state.value.messages.toMutableList().apply { add(userMsg) }

            val assistantId = generateId()
            val assistantMsg = ChatMessageDomainModel(
                id = assistantId, sessionId = session.id, content = "",
                isUser = false, status = MessageStatus.SENDING, timestamp = currentTime(),
                branchId = branchId,
            )
            repository.insertMessage(assistantMsg)
            msgs.add(assistantMsg)
            _state.update { it.copy(messages = msgs, isLoading = true) }

            jobManager.launch(session.id, viewModelScope) { jobState ->
                // Declared outside try so catch block can persist partial data
                val segments = mutableListOf<MessageSegment>()
                var thinkingStartMs = 0L
                try {
                    val chatSession = chatService.createSession(session.id, sessionType)
                    // Persist Deepseek index BEFORE stream: _messages.size = index where
                    // the new user message will be placed by chatStream() (synchronously in flow {}).
                    persistDeepseekIndex(userMsg, chatSession)
                    Logger.info(LogCategory.CHAT, "chat.streamStart", "calling session.chat('${content.take(30)}...', type=${sessionType.name.lowercase()})", sessionId = session.id)
                    chatSession.chat(content).collect { event ->
                        when (event) {
                            is ChatService.StreamEvent.Thinking -> {
                                if (thinkingStartMs == 0L) thinkingStartMs = currentTime()
                                if (jobState.thinkingBuilder == null)
                                    jobState.thinkingBuilder = StringBuilder()
                                jobState.thinkingBuilder!!.append(event.text)
                                // Merge consecutive THINKING segments instead of
                                // creating one per SSE token (which produces hundreds
                                // of tiny rows in the timeline).
                                val lastSeg = segments.lastOrNull()
                                if (lastSeg is MessageSegment.Text && lastSeg.type == TextType.THINKING) {
                                    segments[segments.lastIndex] = MessageSegment.Text(TextType.THINKING, lastSeg.text + event.text)
                                } else {
                                    segments.add(MessageSegment.Text(TextType.THINKING, event.text))
                                }
                                val thinkText = jobState.thinkingBuilder.toString()
                                _state.update {
                                    it.copy(messages = it.messages.map { msg ->
                                        if (msg.id == assistantId)
                                            msg.copy(thinkingContent = thinkText, segments = segments.toList())
                                        else msg
                                    })
                                }
                            }
                            is ChatService.StreamEvent.Content -> {
                                jobState.contentBuilder.append(event.text)
                                // Merge consecutive CONTENT segments (same rationale as
                                // Thinking — avoids one Markdown composable per token).
                                val lastSeg = segments.lastOrNull()
                                if (lastSeg is MessageSegment.Text && lastSeg.type == TextType.CONTENT) {
                                    segments[segments.lastIndex] = MessageSegment.Text(TextType.CONTENT, lastSeg.text + event.text)
                                } else {
                                    segments.add(MessageSegment.Text(TextType.CONTENT, event.text))
                                }
                                _state.update {
                                    it.copy(messages = it.messages.map { msg ->
                                        if (msg.id == assistantId)
                                            msg.copy(
                                                content = jobState.contentBuilder.toString(),
                                                thinkingContent = jobState.thinkingBuilder?.toString(),
                                                segments = segments.toList(),
                                                status = MessageStatus.SENDING,
                                            )
                                        else msg
                                    })
                                }
                            }
                            is ChatService.StreamEvent.ToolCall -> {
                                if (event.functionName == "askuser") {
                                    // Parse askuser args and create interactive segment
                                    val args = parseAskUserArgs(event.arguments)
                                    val seg = MessageSegment.AskUser(
                                        desc = args?.desc ?: "",
                                        timeoutMs = args?.timeoutMs ?: 60_000,
                                        isMulti = args?.isMulti ?: false,
                                        allowCustom = args?.allowCustom ?: false,
                                        options = args?.options ?: emptyList(),
                                        startedAtMs = currentTime(),
                                        answer = null,
                                    )
                                    segments.add(seg)
                                    jobState.pendingAskUserSegments[event.toolCallId] = seg
                                    Logger.info(LogCategory.CHAT, "chat.askuser", "desc=${args?.desc ?: ""} options=${(args?.options ?: emptyList()).size}", sessionId = session.id)
                                    _state.update {
                                        it.copy(messages = it.messages.map { msg ->
                                            if (msg.id == assistantId)
                                                msg.copy(segments = segments.toList(), status = MessageStatus.SENDING)
                                            else msg
                                        })
                                    }
                                } else {
                                    jobState.pendingToolCalls[event.toolCallId] = event
                                    Logger.info(LogCategory.TOOL, "tool.call", "${event.functionName} args=${event.arguments.take(200)}", sessionId = session.id)
                                }
                            }
                            is ChatService.StreamEvent.ToolResult -> {
                                if (thinkingStartMs == 0L) thinkingStartMs = currentTime()
                                if (event.functionName == "askuser") {
                                    // Update the existing AskUser segment with the answer
                                    val existingSeg = jobState.pendingAskUserSegments.remove(event.toolCallId)
                                    if (existingSeg != null) {
                                        val idx = segments.indexOfLast { it is MessageSegment.AskUser && it.desc == existingSeg.desc && it.answer == null }
                                        val elapsed = currentTime() - existingSeg.startedAtMs
                                        val answer = if (event.content == "null") null
                                        else AskUserAnswer(
                                            selected = parseAskUserResult(event.content),
                                            customText = parseAskUserCustomText(event.content),
                                            elapsedMs = elapsed,
                                        )
                                        val updatedSeg = existingSeg.copy(answer = answer)
                                        if (idx >= 0) segments[idx] = updatedSeg
                                        // Record as completed tool call for legacy compatibility
                                        jobState.completedToolCalls.add(ToolCallInfo(
                                            id = event.toolCallId, name = event.functionName,
                                            arguments = "{}", result = event.content, isError = event.isError,
                                        ))
                                        _state.update {
                                            it.copy(
                                                pendingAskUser = it.pendingAskUser?.copy(answer = answer),
                                                messages = it.messages.map { msg ->
                                                    if (msg.id == assistantId)
                                                        msg.copy(
                                                            content = jobState.contentBuilder.toString(),
                                                            toolCalls = jobState.completedToolCalls.toList(),
                                                            thinkingContent = jobState.thinkingBuilder?.toString(),
                                                            segments = segments.toList(),
                                                            status = MessageStatus.SENDING,
                                                        )
                                                    else msg
                                                },
                                            )
                                        }
                                    }
                                    Logger.info(LogCategory.CHAT, "chat.askuserAnswer", "answered=${event.content.take(120)}", sessionId = session.id)
                                } else {
                                    val tc = jobState.pendingToolCalls.remove(event.toolCallId)
                                    val info = ToolCallInfo(
                                        id = event.toolCallId,
                                        name = event.functionName,
                                        arguments = tc?.arguments ?: "{}",
                                        result = event.content,
                                        isError = event.isError,
                                    )
                                    jobState.completedToolCalls.add(info)
                                    segments.add(MessageSegment.ToolCall(info))
                                    Logger.info(LogCategory.TOOL, "tool.result", "${event.functionName} ok=${!event.isError} len=${event.content.length}", sessionId = session.id)
                                    _state.update {
                                        it.copy(
                                            messages = it.messages.map { msg ->
                                                if (msg.id == assistantId)
                                                    msg.copy(
                                                        content = jobState.contentBuilder.toString(),
                                                        toolCalls = jobState.completedToolCalls.toList(),
                                                        thinkingContent = jobState.thinkingBuilder?.toString(),
                                                        segments = segments.toList(),
                                                        status = MessageStatus.SENDING,
                                                    )
                                                else msg
                                            }
                                        )
                                    }
                                }
                            }
                            is ChatService.StreamEvent.Done -> {
                                Logger.info(LogCategory.CHAT, "chat.streamDone", "done: ${event.totalTokens} tokens, reason=${event.finishReason}, ${jobState.contentBuilder.length} chars", sessionId = session.id)
                                val finalContent = jobState.contentBuilder.toString()
                                val finalThinking = jobState.thinkingBuilder?.toString()
                                val finalToolCalls = jobState.completedToolCalls.toList().ifEmpty { null }
                                val finalSegments = segments.toList()
                                val thinkingElapsed = if (thinkingStartMs > 0) ((currentTime() - thinkingStartMs) / 1000).toInt() else null
                                Logger.assistantMessage(
                                    sessionId = session.id,
                                    messageId = assistantId,
                                    content = finalContent,
                                    thinking = finalThinking,
                                    segmentsJson = json.encodeToString(finalSegments),
                                    toolCallsJson = json.encodeToString(finalToolCalls ?: emptyList()),
                                    finishReason = event.finishReason,
                                    totalTokens = event.totalTokens,
                                    durationMs = if (thinkingStartMs > 0) currentTime() - thinkingStartMs else null,
                                )
                                // Append truncation warning if model was cut off
                                val displayContent = when (event.finishReason) {
                                    "length" -> finalContent + "\n\n⚠️ 响应因超出 token 限制被截断"
                                    "content_filter" -> finalContent + "\n\n⚠️ 响应内容被过滤"
                                    "insufficient_system_resource" -> finalContent + "\n\n⚠️ 请求因资源不足被中断"
                                    else -> finalContent
                                }
                                repository.updateMessage(assistantMsg.copy(
                                    content = displayContent,
                                    thinkingContent = finalThinking,
                                    toolCalls = finalToolCalls,
                                    segments = finalSegments,
                                    thinkingElapsedSec = thinkingElapsed,
                                    totalTokens = event.totalTokens,
                                    finishReason = event.finishReason,
                                    status = MessageStatus.SENT,
                                ))
                                repository.updateSession(session.copy(lastModified = currentTime()))
                                _state.update {
                                    it.copy(isLoading = false, messages = it.messages.map { msg ->
                                        if (msg.id == assistantId)
                                            msg.copy(
                                                content = displayContent,
                                                thinkingContent = finalThinking,
                                                toolCalls = finalToolCalls,
                                                totalTokens = event.totalTokens,
                                                finishReason = event.finishReason,
                                                segments = finalSegments,
                                                thinkingElapsedSec = thinkingElapsed,
                                                status = MessageStatus.SENT,
                                            )
                                        else msg
                                    })
                                }
                            }
                            is ChatService.StreamEvent.Error -> {
                                cancelPendingAskUser()
                                Logger.warn(LogCategory.CHAT, "chat.streamError", event.message, sessionId = session.id)
                                val finalThinking = jobState.thinkingBuilder?.toString()
                                val finalToolCalls = jobState.completedToolCalls.toList().ifEmpty { null }
                                val finalSegments = segments.toList()
                                val thinkingElapsed = if (thinkingStartMs > 0) ((currentTime() - thinkingStartMs) / 1000).toInt() else null
                                repository.updateMessage(assistantMsg.copy(
                                    thinkingContent = finalThinking,
                                    toolCalls = finalToolCalls,
                                    segments = finalSegments,
                                    thinkingElapsedSec = thinkingElapsed,
                                    totalTokens = null,
                                    finishReason = null,
                                    status = MessageStatus.ERROR,
                                ))
                                _state.update {
                                    it.copy(isLoading = false, error = event.message,
                                        messages = it.messages.map { msg ->
                                            if (msg.id == assistantId)
                                                msg.copy(
                                                    status = MessageStatus.ERROR,
                                                    thinkingContent = finalThinking,
                                                    toolCalls = finalToolCalls,
                                                    segments = finalSegments,
                                                    thinkingElapsedSec = thinkingElapsed,
                                                    totalTokens = null,
                                                    finishReason = null,
                                                )
                                            else msg
                                        })
                                }
                            }
                            // ── Coach-specific events ──
                            is ChatService.StreamEvent.CoachInit -> {
                                val pid = event.pid
                                segments.add(MessageSegment.ProblemCard(
                                    pid = pid,
                                    coachContent = event.content,
                                ))
                                _state.update {
                                    it.copy(messages = it.messages.map { msg ->
                                        if (msg.id == assistantId)
                                            msg.copy(segments = segments.toList())
                                        else msg
                                    })
                                }
                                fetchProblemDetail(pid, assistantId, assistantMsg)
                            }
                            is ChatService.StreamEvent.CoachFinished -> {
                                if (event.recommend != null) {
                                    for (pid in event.recommend) {
                                        repository.insertRecommendation(session.id, pid)
                                    }
                                }
                                jobState.contentBuilder.append("\n\n---\n${event.content}")
                                _state.update {
                                    it.copy(messages = it.messages.map { msg ->
                                        if (msg.id == assistantId)
                                            msg.copy(content = jobState.contentBuilder.toString())
                                        else msg
                                    })
                                }
                            }
                            is ChatService.StreamEvent.CoachCheckpoint -> {
                                // Content already emitted via Thinking/Content; mark checkpoint in message
                                jobState.contentBuilder.append("\n\n📋 检查点")
                                _state.update {
                                    it.copy(messages = it.messages.map { msg ->
                                        if (msg.id == assistantId)
                                            msg.copy(content = jobState.contentBuilder.toString())
                                        else msg
                                    })
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    cancelPendingAskUser()
                    Logger.error(LogCategory.CHAT, "chat.streamError", e.message ?: "Unknown error", sessionId = session.id)
                    val finalThinking = jobState.thinkingBuilder?.toString()
                    val finalToolCalls = jobState.completedToolCalls.toList().ifEmpty { null }
                    val finalSegments = segments.toList()
                    val thinkingElapsed = if (thinkingStartMs > 0) ((currentTime() - thinkingStartMs) / 1000).toInt() else null
                    repository.updateMessage(assistantMsg.copy(
                        thinkingContent = finalThinking,
                        toolCalls = finalToolCalls,
                        segments = finalSegments,
                        thinkingElapsedSec = thinkingElapsed,
                        totalTokens = null,
                        finishReason = null,
                        status = MessageStatus.ERROR,
                    ))
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: "Unknown error",
                            messages = it.messages.map { msg ->
                                if (msg.id == assistantId)
                                    msg.copy(
                                        status = MessageStatus.ERROR,
                                        thinkingContent = finalThinking,
                                        toolCalls = finalToolCalls,
                                        segments = finalSegments,
                                        thinkingElapsedSec = thinkingElapsed,
                                        totalTokens = null,
                                        finishReason = null,
                                    )
                                else msg
                            })
                    }
                }
            }
        }
    }

    // ── AskUser public API ──

    /** Called from UI when user submits an answer to an askuser question. */
    fun submitAskUserAnswer(selectedOptions: List<String>, customText: String) {
        val result = buildString {
            append("[")
            append(selectedOptions.joinToString(",") { "\"$it\"" })
            append("]")
            if (customText.isNotBlank()) {
                append("|custom:")
                append(customText)
            }
        }
        val questionId = _state.value.pendingAskUser?.questionId
        viewModelScope.launch {
            if (questionId != null) {
                chatService.submitUserAnswer(questionId, result)
            }
            _state.update { it.copy(pendingAskUser = null) }
        }
    }

    /** Cancel any pending askuser (session switch, error, etc.). */
    private fun cancelPendingAskUser() {
        _state.update { it.copy(pendingAskUser = null) }
    }

    // ── AskUser JSON helpers ──

    /** Parse selected options from askuser tool result. */
    private fun parseAskUserResult(content: String): List<String> {
        // Content is either a JSON array like ["option1","option2"] or "null"
        if (content == "null") return emptyList()
        // Try to parse the part before "|custom:" separator
        val jsonPart = content.substringBefore("|custom:")
        return try {
            json.parseToJsonElement(jsonPart).jsonArray.map { it.jsonPrimitive.content }
        } catch (_: Exception) { emptyList() }
    }

    /** Parse custom text from askuser tool result. */
    private fun parseAskUserCustomText(content: String): String? {
        val idx = content.indexOf("|custom:")
        if (idx < 0) return null
        return content.substring(idx + 8)
    }

    private fun selectSession(session: ChatSessionDomainModel) {
        cancelPendingAskUser()
        Logger.info(LogCategory.CHAT, "chat.selectSession", "session=${session.id} type=${session.type}")
        viewModelScope.launch {
            _state.update { it.copy(currentSession = session, sessionType = session.type, showHomeScreen = false, messages = emptyList(), activeBranchId = session.lastActiveBranchId) }
            // Load branches for this session — ensure 'main' exists
            val branches = repository.getBranchesBySession(session.id).toMutableList()
            if (branches.none { it.id == "main" }) {
                val mainBranch = ChatBranchDomainModel(id = "main", sessionId = session.id, createdAt = session.createdAt)
                repository.insertBranch(mainBranch)
                branches.add(mainBranch)
            }
            // Load messages for active branch
            val msgs = repository.getMessagesByBranch(session.id, session.lastActiveBranchId)
                .sortedBy { it.timestamp }
            _state.update { it.copy(messages = msgs, branches = branches) }
            ensureDeepseekIndices(session.id, msgs)
        }
    }

    /**
     * Replay all messages through a fresh ChatSession to rebuild [ChatMessageDomainModel.deepseekMessageIndex].
     * Needed because historical messages in DB have NULL indices (column added after data existed).
     */
    private suspend fun ensureDeepseekIndices(sessionId: String, messages: List<ChatMessageDomainModel>) {
        val needsBackfill = messages.any { it.isUser && it.deepseekMessageIndex == null }
        if (!needsBackfill) return

        val chatSession = chatService.resetSession(sessionId, _state.value.sessionType)
        for (msg in messages.sortedBy { it.timestamp }) {
            val role = if (msg.isUser) io.github.hatoyuze.deepseek.protocol.api.entity.Role.User
            else io.github.hatoyuze.deepseek.protocol.api.entity.Role.Assistance
            val newIndex = chatSession.addMessageToHistory(
                io.github.hatoyuze.deepseek.protocol.api.entity.Message(role, content = msg.content)
            )
            if (msg.isUser && newIndex != msg.deepseekMessageIndex) {
                repository.updateMessage(msg.copy(deepseekMessageIndex = newIndex))
            }
        }
    }

    private fun createNewSession(type: SessionType) {
        Logger.info(LogCategory.CHAT, "chat.newSession", "type=${type}")
        _state.update { it.copy(currentSession = null, sessionType = type, showHomeScreen = false, messages = emptyList(), activeBranchId = "main") }
    }

    private fun selectModel(model: String) {
        Logger.info(LogCategory.CHAT, "chat.selectModel", "model=$model")
        ConfigService.model = model
        _state.update { it.copy(selectedModel = model, showModelList = false) }
    }

    private fun retryMessage(message: ChatMessageDomainModel) {
        Logger.info(LogCategory.CHAT, "chat.retry", "message=${message.id}")
        val messages = _state.value.messages
        val idx = messages.indexOfFirst { it.id == message.id }
        val lastUserMsg = messages.take(idx).findLast { it.isUser }
        if (lastUserMsg != null) sendMessage(lastUserMsg.content)
    }

    // ── Stop generation ──

    private fun stopGeneration() {
        val session = _state.value.currentSession ?: return
        Logger.info(LogCategory.CHAT, "chat.stop", "session=${session.id}")
        viewModelScope.launch {
            val chatSession = chatService.createSession(session.id, _state.value.sessionType)
            chatSession.cancelGeneration()
            jobManager.cancel(session.id)
            cancelPendingAskUser()

            val currentMsgs = _state.value.messages.toMutableList()
            val lastIdx = currentMsgs.indexOfLast {
                !it.isUser && it.status == MessageStatus.SENDING
            }
            if (lastIdx >= 0) {
                val msg = currentMsgs[lastIdx]
                val abortedMsg = msg.copy(
                    status = MessageStatus.ABORTED,
                    finishReason = "aborted",
                )
                currentMsgs[lastIdx] = abortedMsg
                repository.updateMessage(abortedMsg)
            }
            _state.update { it.copy(isLoading = false, messages = currentMsgs) }
        }
    }

    // ── Edit message with fork support ──

    private fun sendEdit(messageId: String, newContent: String) {
        Logger.info(LogCategory.CHAT, "chat.edit", "message=$messageId")
        val msgs = _state.value.messages
        val msgIdx = msgs.indexOfFirst { it.id == messageId }
        if (msgIdx < 0) return
        val oldMsg = msgs[msgIdx]
        if (!oldMsg.isUser) return

        viewModelScope.launch {
            val domainSession = _state.value.currentSession ?: return@launch

            // ── EVERY edit creates a new branch (git checkout -b) ──
            val newBranchId = "branch_${currentTime()}"

            // 1. Copy ancestors BEFORE fork point to new branch (excludes fork point itself)
            //    Track copies for in-memory state construction
            val ancestors = msgs.take(msgIdx)
            val ancestorCopies = mutableListOf<ChatMessageDomainModel>()
            var prevCopyId: String? = null
            for (ancestor in ancestors) {
                val copy = ancestor.copy(
                    id = generateId(),
                    branchId = newBranchId,
                    parentMessageId = prevCopyId,
                )
                repository.insertMessage(copy)
                ancestorCopies.add(copy)
                prevCopyId = copy.id
            }

            // 2. Create edited user message — this IS the fork point on the new branch
            val userMsg = ChatMessageDomainModel(
                id = generateId(), sessionId = domainSession.id,
                content = newContent, isUser = true,
                status = MessageStatus.SENT, timestamp = currentTime(),
                branchId = newBranchId,
                parentMessageId = prevCopyId,
            )
            repository.insertMessage(userMsg)

            // 3. Create the new branch record
            repository.insertBranch(ChatBranchDomainModel(
                id = newBranchId,
                sessionId = domainSession.id,
                parentBranchId = _state.value.activeBranchId,
                forkMessageId = oldMsg.id,
                editedMessageId = userMsg.id,
                createdAt = currentTime(),
            ))

            // 4. Create SENDING assistant on new branch
            val assistantMsg = ChatMessageDomainModel(
                id = generateId(), sessionId = domainSession.id,
                content = "", isUser = false,
                status = MessageStatus.SENDING, timestamp = currentTime(),
                branchId = newBranchId,
                parentMessageId = userMsg.id,
            )
            repository.insertMessage(assistantMsg)

            // 5. Build new-branch message list IN-MEMORY and set state DIRECTLY
            val branchMessages = ancestorCopies + listOf(userMsg, assistantMsg)
            val branches = repository.getBranchesBySession(domainSession.id)
            _state.update {
                it.copy(
                    activeBranchId = newBranchId,
                    messages = branchMessages,
                    branches = branches,
                    editingMessageId = null,
                )
            }
            // Persist the branch switch so reloads use the correct branch
            repository.updateSession(domainSession.copy(lastActiveBranchId = newBranchId))

            // 6. Reset Deepseek and replay ancestors for API context
            val chatSession = chatService.resetSession(domainSession.id, _state.value.sessionType)
            for (msg in ancestorCopies) {
                chatSession.addMessageToHistory(msg.toProtocolMessage())
            }

            // 7. Persist deepseek index and stream
            persistDeepseekIndex(userMsg, chatSession)
            jobManager.launch(domainSession.id, viewModelScope) { jobState ->
                streamAssistantResponse(domainSession, assistantMsg, chatSession, jobState,
                    chatContent = newContent)
            }
        }
    }

    // ── Delete a user+assistant exchange ──

    private fun deleteExchange(userMessageId: String, assistantMessageId: String) {
        Logger.info(LogCategory.CHAT, "chat.deleteMessages", "user=$userMessageId assistant=$assistantMessageId")
        val msgs = _state.value.messages
        val userMsg = msgs.find { it.id == userMessageId } ?: return
        viewModelScope.launch {
            val deepseekUserIdx = resolveDeepseekIndex(userMsg)
            if (deepseekUserIdx == null) {
                _state.update { it.copy(toast = "无法定位消息位置，请重新发送消息后再试") }
                return@launch
            }
            repository.deleteMessage(userMessageId)
            repository.deleteMessage(assistantMessageId)
            _state.update { state ->
                state.copy(messages = state.messages.filter {
                    it.id != userMessageId && it.id != assistantMessageId
                })
            }
            val session = chatService.createSession(
                _state.value.currentSession!!.id, _state.value.sessionType)
            session.truncateAt(deepseekUserIdx - 1)
        }
    }

    // ── Regenerate: delete old assistant, create SENDING bubble, re-stream ──

    private fun regenerateMessage(messageId: String) {
        Logger.info(LogCategory.CHAT, "chat.regenerate", "message=$messageId")
        val msgs = _state.value.messages
        val msgIdx = msgs.indexOfFirst { it.id == messageId }
        if (msgIdx < 0) return

        val hasLaterUserMsg = msgs.drop(msgIdx + 1).any { it.isUser }
        if (hasLaterUserMsg) {
            _state.update { it.copy(toast = "只能重新生成最新的消息") }
            return
        }

        // Regenerate is dispatched with user message ID (ChatScreen finds preceding user)
        val userMsg = msgs[msgIdx]
        if (!userMsg.isUser) {
            _state.update { it.copy(toast = "无法定位消息位置") }
            return
        }
        val session = _state.value.currentSession ?: return
        viewModelScope.launch {
            val deepseekUserIdx = resolveDeepseekIndex(userMsg)
            if (deepseekUserIdx == null) {
                _state.update { it.copy(toast = "无法定位消息位置") }
                return@launch
            }
            val chatSession = chatService.createSession(session.id, _state.value.sessionType)

            // 1. Truncate Deepseek: keep user message, remove old assistant + tool msgs
            chatSession.truncateAt(deepseekUserIdx)

            // 2. Delete old assistant from DB and state
            val oldAssistant = msgs.getOrNull(msgIdx + 1)?.takeUnless { it.isUser }
            if (oldAssistant != null) {
                repository.deleteMessage(oldAssistant.id)
                _state.update { it.copy(messages = it.messages.filter { m -> m.id != oldAssistant.id }) }
            }

            // 3. Create new SENDING assistant bubble
            val newId = generateId()
            val newAssistant = ChatMessageDomainModel(
                id = newId, sessionId = session.id, content = "",
                isUser = false, status = MessageStatus.SENDING, timestamp = currentTime(),
                branchId = _state.value.activeBranchId,
            )
            repository.insertMessage(newAssistant)
            _state.update { it.copy(isLoading = true, messages = it.messages + newAssistant) }

            // 4. Stream via continueChat() — no user message added to _messages
            jobManager.launch(session.id, viewModelScope) { jobState ->
                streamAssistantResponse(session, newAssistant, chatSession, jobState,
                    useContinueChat = true)
            }
        }
    }

    /**
     * Stream assistant response into an existing SENDING bubble.
     * Shared between [sendMessage] and [regenerateMessage].
     */
    private suspend fun streamAssistantResponse(
        session: ChatSessionDomainModel,
        assistantMsg: ChatMessageDomainModel,
        chatSession: ChatSession,
        jobState: com.github.hatoyuze.luogu.gui.presentation.utils.ChatJobManager.JobState,
        useContinueChat: Boolean = false,
        chatContent: String = assistantMsg.content,
    ) {
        val assistantId = assistantMsg.id
        val segments = mutableListOf<MessageSegment>()
        var thinkingStartMs = 0L
        try {
            val flow = if (useContinueChat) chatSession.continueChat()
            else chatSession.chat(chatContent)
            // regenerateMessage doesn't call this with useContinueChat=false
            flow.collect { event ->
                when (event) {
                    is ChatService.StreamEvent.Thinking -> {
                        if (thinkingStartMs == 0L) thinkingStartMs = currentTime()
                        if (jobState.thinkingBuilder == null) jobState.thinkingBuilder = StringBuilder()
                        jobState.thinkingBuilder!!.append(event.text)
                        val lastSeg = segments.lastOrNull()
                        if (lastSeg is MessageSegment.Text && lastSeg.type == TextType.THINKING) {
                            segments[segments.lastIndex] = MessageSegment.Text(TextType.THINKING, lastSeg.text + event.text)
                        } else {
                            segments.add(MessageSegment.Text(TextType.THINKING, event.text))
                        }
                        _state.update { it.copy(messages = it.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(thinkingContent = jobState.thinkingBuilder.toString(), segments = segments.toList()) else msg
                        }) }
                    }
                    is ChatService.StreamEvent.Content -> {
                        jobState.contentBuilder.append(event.text)
                        val lastSeg = segments.lastOrNull()
                        if (lastSeg is MessageSegment.Text && lastSeg.type == TextType.CONTENT) {
                            segments[segments.lastIndex] = MessageSegment.Text(TextType.CONTENT, lastSeg.text + event.text)
                        } else {
                            segments.add(MessageSegment.Text(TextType.CONTENT, event.text))
                        }
                        _state.update { it.copy(messages = it.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(content = jobState.contentBuilder.toString(), thinkingContent = jobState.thinkingBuilder?.toString(), segments = segments.toList(), status = MessageStatus.SENDING) else msg
                        }) }
                    }
                    is ChatService.StreamEvent.ToolCall -> {
                        if (event.functionName == "askuser") {
                            val args = parseAskUserArgs(event.arguments)
                            val seg = MessageSegment.AskUser(
                                desc = args?.desc ?: "",
                                timeoutMs = args?.timeoutMs ?: 60_000,
                                isMulti = args?.isMulti ?: false,
                                allowCustom = args?.allowCustom ?: false,
                                options = args?.options ?: emptyList(),
                                startedAtMs = currentTime(),
                                answer = null,
                            )
                            segments.add(seg)
                            jobState.pendingAskUserSegments[event.toolCallId] = seg
                            Logger.info(LogCategory.CHAT, "chat.askuser", "desc=${args?.desc ?: ""} options=${(args?.options ?: emptyList()).size}", sessionId = session.id)
                            _state.update { it.copy(messages = it.messages.map { msg -> if (msg.id == assistantId) msg.copy(segments = segments.toList(), status = MessageStatus.SENDING) else msg }) }
                        } else {
                            jobState.pendingToolCalls[event.toolCallId] = event
                            Logger.info(LogCategory.TOOL, "tool.call", "${event.functionName} args=${event.arguments.take(200)}", sessionId = session.id)
                        }
                    }
                    is ChatService.StreamEvent.ToolResult -> {
                        if (thinkingStartMs == 0L) thinkingStartMs = currentTime()
                        if (event.functionName == "askuser") {
                            val existingSeg = jobState.pendingAskUserSegments.remove(event.toolCallId)
                            if (existingSeg != null) {
                                val sIdx = segments.indexOfLast { it is MessageSegment.AskUser && it.desc == existingSeg.desc && it.answer == null }
                                val answer = if (event.content == "null") null else AskUserAnswer(selected = parseAskUserResult(event.content), customText = parseAskUserCustomText(event.content), elapsedMs = currentTime() - existingSeg.startedAtMs)
                                if (sIdx >= 0) segments[sIdx] = existingSeg.copy(answer = answer)
                                jobState.completedToolCalls.add(ToolCallInfo(id = event.toolCallId, name = event.functionName, arguments = "{}", result = event.content, isError = event.isError))
                                _state.update { it.copy(pendingAskUser = it.pendingAskUser?.copy(answer = answer), messages = it.messages.map { msg -> if (msg.id == assistantId) msg.copy(content = jobState.contentBuilder.toString(), toolCalls = jobState.completedToolCalls.toList(), thinkingContent = jobState.thinkingBuilder?.toString(), segments = segments.toList(), status = MessageStatus.SENDING) else msg }) }
                            }
                            Logger.info(LogCategory.CHAT, "chat.askuserAnswer", "answered=${event.content.take(120)}", sessionId = session.id)
                        } else {
                            val tc = jobState.pendingToolCalls.remove(event.toolCallId)
                            val info = ToolCallInfo(id = event.toolCallId, name = event.functionName, arguments = tc?.arguments ?: "{}", result = event.content, isError = event.isError)
                            jobState.completedToolCalls.add(info)
                            segments.add(MessageSegment.ToolCall(info))
                            Logger.info(LogCategory.TOOL, "tool.result", "${event.functionName} ok=${!event.isError} len=${event.content.length}", sessionId = session.id)
                            _state.update { it.copy(messages = it.messages.map { msg -> if (msg.id == assistantId) msg.copy(content = jobState.contentBuilder.toString(), toolCalls = jobState.completedToolCalls.toList(), thinkingContent = jobState.thinkingBuilder?.toString(), segments = segments.toList(), status = MessageStatus.SENDING) else msg }) }
                        }
                    }
                    is ChatService.StreamEvent.Done -> {
                        val finalContent = jobState.contentBuilder.toString()
                        val displayContent = when (event.finishReason) {
                            "length" -> finalContent + "\n\n⚠️ 响应因超出 token 限制被截断"
                            "content_filter" -> finalContent + "\n\n⚠️ 响应内容被过滤"
                            "insufficient_system_resource" -> finalContent + "\n\n⚠️ 请求因资源不足被中断"
                            else -> finalContent
                        }
                        val thinkingElapsed = if (thinkingStartMs > 0) ((currentTime() - thinkingStartMs) / 1000).toInt() else null
                        Logger.assistantMessage(
                            sessionId = session.id,
                            messageId = assistantId,
                            content = finalContent,
                            thinking = jobState.thinkingBuilder?.toString(),
                            segmentsJson = json.encodeToString(segments),
                            toolCallsJson = json.encodeToString(jobState.completedToolCalls.toList()),
                            finishReason = event.finishReason,
                            totalTokens = event.totalTokens,
                            durationMs = if (thinkingStartMs > 0) currentTime() - thinkingStartMs else null,
                        )
                        repository.updateMessage(assistantMsg.copy(content = displayContent, thinkingContent = jobState.thinkingBuilder?.toString(), toolCalls = jobState.completedToolCalls.toList().ifEmpty { null }, segments = segments.toList(), thinkingElapsedSec = thinkingElapsed, totalTokens = event.totalTokens, finishReason = event.finishReason, status = MessageStatus.SENT))
                        repository.updateSession(session.copy(lastModified = currentTime()))
                        _state.update { state ->
                            val activeBranch = state.activeBranchId
                            state.copy(isLoading = false, messages = state.messages
                                .filter { it.branchId == activeBranch }
                                .map { msg -> if (msg.id == assistantId) msg.copy(content = displayContent, thinkingContent = jobState.thinkingBuilder?.toString(), toolCalls = jobState.completedToolCalls.toList().ifEmpty { null }, totalTokens = event.totalTokens, finishReason = event.finishReason, segments = segments.toList(), thinkingElapsedSec = thinkingElapsed, status = MessageStatus.SENT) else msg })
                        }
                    }
                    is ChatService.StreamEvent.Error -> {
                        cancelPendingAskUser()
                        Logger.warn(LogCategory.CHAT, "chat.streamError", event.message, sessionId = session.id)
                        val thinkingElapsed = if (thinkingStartMs > 0) ((currentTime() - thinkingStartMs) / 1000).toInt() else null
                        repository.updateMessage(assistantMsg.copy(thinkingContent = jobState.thinkingBuilder?.toString(), toolCalls = jobState.completedToolCalls.toList().ifEmpty { null }, segments = segments.toList(), thinkingElapsedSec = thinkingElapsed, totalTokens = null, finishReason = null, status = MessageStatus.ERROR))
                        _state.update { state ->
                            val activeBranch = state.activeBranchId
                            state.copy(isLoading = false, error = event.message, messages = state.messages
                                .filter { it.branchId == activeBranch }
                                .map { msg -> if (msg.id == assistantId) msg.copy(status = MessageStatus.ERROR, thinkingContent = jobState.thinkingBuilder?.toString(), toolCalls = jobState.completedToolCalls.toList().ifEmpty { null }, segments = segments.toList(), thinkingElapsedSec = thinkingElapsed, totalTokens = null, finishReason = null) else msg })
                        }
                    }
                    is ChatService.StreamEvent.CoachInit -> {
                        segments.add(MessageSegment.ProblemCard(pid = event.pid, coachContent = event.content))
                        _state.update { it.copy(messages = it.messages.map { msg -> if (msg.id == assistantId) msg.copy(segments = segments.toList()) else msg }) }
                        fetchProblemDetail(event.pid, assistantId, assistantMsg)
                    }
                    is ChatService.StreamEvent.CoachFinished -> {
                        if (event.recommend != null) { for (pid in event.recommend) { repository.insertRecommendation(session.id, pid) } }
                        jobState.contentBuilder.append("\n\n---\n${event.content}")
                        _state.update { it.copy(messages = it.messages.map { msg -> if (msg.id == assistantId) msg.copy(content = jobState.contentBuilder.toString()) else msg }) }
                    }
                    is ChatService.StreamEvent.CoachCheckpoint -> {
                        jobState.contentBuilder.append("\n\n📋 检查点")
                        _state.update { it.copy(messages = it.messages.map { msg -> if (msg.id == assistantId) msg.copy(content = jobState.contentBuilder.toString()) else msg }) }
                    }
                }
            }
        } catch (e: Exception) {
            cancelPendingAskUser()
            Logger.error(LogCategory.CHAT, "chat.streamError", e.message ?: "Unknown error", sessionId = session.id)
            val thinkingElapsed = if (thinkingStartMs > 0) ((currentTime() - thinkingStartMs) / 1000).toInt() else null
            repository.updateMessage(assistantMsg.copy(thinkingContent = jobState.thinkingBuilder?.toString(), toolCalls = jobState.completedToolCalls.toList().ifEmpty { null }, segments = segments.toList(), thinkingElapsedSec = thinkingElapsed, totalTokens = null, finishReason = null, status = MessageStatus.ERROR))
            _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error", messages = it.messages.map { msg -> if (msg.id == assistantId) msg.copy(status = MessageStatus.ERROR, thinkingContent = jobState.thinkingBuilder?.toString(), toolCalls = jobState.completedToolCalls.toList().ifEmpty { null }, segments = segments.toList(), thinkingElapsedSec = thinkingElapsed, totalTokens = null, finishReason = null) else msg }) }
        }
    }

    // ── Branch switching ──

    private suspend fun switchToBranch(branchId: String) {
        val session = _state.value.currentSession ?: return
        Logger.info(LogCategory.CHAT, "chat.switchBranch", "branch=$branchId session=${session.id}")
        switchToBranch(branchId, session)
    }

    private suspend fun switchToBranch(branchId: String, session: ChatSessionDomainModel) {
        val branchMessages = repository.getMessagesByBranch(session.id, branchId)
            .sortedBy { it.timestamp }
        val branches = repository.getBranchesBySession(session.id)

        val newSession = chatService.resetSession(session.id, _state.value.sessionType)

        // Replay complete branch history into Deepseek, rebuilding deepseekMessageIndex
        for (msg in branchMessages) {
            val newIndex = newSession.addMessageToHistory(msg.toProtocolMessage())
            if (msg.isUser && newIndex != msg.deepseekMessageIndex) {
                repository.updateMessage(msg.copy(deepseekMessageIndex = newIndex))
            }
            // Replay tool results for assistant messages with tool calls
            msg.toolCalls?.forEach { tc ->
                if (tc.result != null) {
                    newSession.addMessageToHistory(
                        io.github.hatoyuze.deepseek.protocol.api.entity.Message(
                            role = io.github.hatoyuze.deepseek.protocol.api.entity.Role.Tool,
                            content = tc.result,
                            toolCallId = tc.id,
                            name = tc.name,
                        )
                    )
                }
            }
        }

        _state.update {
            it.copy(
                activeBranchId = branchId,
                messages = branchMessages,
                branches = branches,
            )
        }

        repository.updateSession(session.copy(lastActiveBranchId = branchId))
    }

    // ── Protocol message conversion ──

    private fun ChatMessageDomainModel.toProtocolMessage(): io.github.hatoyuze.deepseek.protocol.api.entity.Message {
        return io.github.hatoyuze.deepseek.protocol.api.entity.Message(
            role = if (isUser) io.github.hatoyuze.deepseek.protocol.api.entity.Role.User
            else io.github.hatoyuze.deepseek.protocol.api.entity.Role.Assistance,
            content = content,
            toolCalls = toolCalls?.map { tc ->
                io.github.hatoyuze.deepseek.toolcall.executor.ToolCall(
                    id = tc.id,
                    name = tc.name,
                    arguments = tc.arguments,
                )
            },
        )
    }

    /**
     * Resolve the Deepseek index for a user message.
     * First tries the DB-cached [ChatMessageDomainModel.deepseekMessageIndex],
     * then falls back to scanning [Deepseek._messages] via [ChatSession.findUserMessageIndex].
     */
    private suspend fun resolveDeepseekIndex(userMsg: ChatMessageDomainModel): Int? {
        userMsg.deepseekMessageIndex?.let { return it }
        // Fallback: scan the live Deepseek._messages
        val session = _state.value.currentSession ?: return null
        val chatSession = chatService.createSession(session.id, _state.value.sessionType)
        val idx = chatSession.findUserMessageIndex(userMsg.content)
        return if (idx >= 0) idx else null
    }

    // ── DeepseekMessageIndex persistence in sendMessage ──
    // (called from sendMessage after ChatSession is obtained)
    private suspend fun persistDeepseekIndex(userMsg: ChatMessageDomainModel, chatSession: ChatSession) {
        val deepseekIdx = chatSession.getMessageCount()  // current size = index where next add() goes
        if (deepseekIdx > 0) {
            val updated = userMsg.copy(deepseekMessageIndex = deepseekIdx)
            repository.updateMessage(updated)
            _state.update { state ->
                state.copy(messages = state.messages.map {
                    if (it.id == userMsg.id) updated else it
                })
            }
        }
    }

    private fun goHome() {
        cancelPendingAskUser()
        Logger.info(LogCategory.CHAT, "chat.goHome", "go home")
        _state.update { it.copy(currentSession = null, showHomeScreen = true, messages = emptyList()) }
    }

    private fun observeSessions() {
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            repository.getAllSessions().collect { sessions ->
                _state.update { it.copy(chatSessions = sessions) }
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch(ioDispatcher) {
            val models = chatService.availableModels()
            _state.update { it.copy(availableModels = models) }
        }
    }

    /**
     * Fetches problem detail from Luogu API and updates the [MessageSegment.ProblemCard]
     * segment in the assistant message. Launched on [CoachInit].
     */
    private fun fetchProblemDetail(
        pid: String,
        assistantId: String,
        assistantMsg: ChatMessageDomainModel,
    ) {
        viewModelScope.launch {
            // Guard: verify message still exists (user might have switched sessions)
            if (_state.value.messages.none { it.id == assistantId }) return@launch

            val data = try {
                chatService.getProblemDetail(pid)
            } catch (_: Exception) { null }

            // Read current segments from state to avoid stale reference
            val currentSegments = _state.value.messages
                .find { it.id == assistantId }?.segments?.toMutableList() ?: return@launch
            val idx = currentSegments.indexOfFirst {
                it is MessageSegment.ProblemCard && it.pid == pid && it.loading
            }
            if (idx < 0) return@launch

            val updated = if (data != null) {
                MessageSegment.ProblemCard(pid = pid, loading = false, data = data)
            } else {
                MessageSegment.ProblemCard(pid = pid, loading = false, error = "获取题目详情失败")
            }
            currentSegments[idx] = updated

            repository.updateMessage(assistantMsg.copy(
                segments = currentSegments.toList(),
                status = MessageStatus.SENDING,
            ))
            _state.update {
                it.copy(messages = it.messages.map { msg ->
                    if (msg.id == assistantId) msg.copy(segments = currentSegments.toList())
                    else msg
                })
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { jobManager.cancelAll() }
        sessionsJob?.cancel()
    }

    private fun generateId() = "${currentTime()}-${kotlin.random.Random.nextInt(10000, 99999)}"
    private fun currentTime() = currentTimeMillis()
}
