package com.github.hatoyuze.luogu.gui.presentation.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LRU-based job manager for concurrent chat sessions.
 * Ported from KOllama's ChatJobManager.
 */
class ChatJobManager(private val maxConcurrent: Int = 5) {
    private val lock = Mutex()
    private val jobMap = mutableMapOf<String, JobState>()
    private val lruList = mutableListOf<String>()

    class JobState(
        var job: Job = Job(),
        val contentBuilder: StringBuilder = StringBuilder(),
        var thinkingBuilder: StringBuilder? = null,
        var isThinking: Boolean = false,
    val pendingToolCalls: MutableMap<String, com.github.hatoyuze.luogu.gui.domain.chat.ChatService.StreamEvent.ToolCall> = mutableMapOf(),
        val completedToolCalls: MutableList<com.github.hatoyuze.luogu.gui.domain.model.ToolCallInfo> = mutableListOf(),
        /** Track AskUser segments created by ToolCall for later update on ToolResult. */
        val pendingAskUserSegments: MutableMap<String, com.github.hatoyuze.luogu.gui.domain.model.MessageSegment.AskUser> = mutableMapOf(),
    )

    suspend fun launch(
        sessionId: String,
        scope: CoroutineScope,
        block: suspend (JobState) -> Unit,
    ): Job {
        lock.withLock {
            // Evict oldest if at capacity and session is new
            if (sessionId !in jobMap && jobMap.size >= maxConcurrent) {
                val oldest = lruList.removeFirstOrNull()
                if (oldest != null) {
                    jobMap[oldest]?.job?.cancel()
                    jobMap.remove(oldest)
                }
            }
            // Move to front (most recent)
            lruList.remove(sessionId)
            lruList.add(sessionId)
        }

        val jobState = JobState(
            job = Job(),
            contentBuilder = StringBuilder(),
        )

        val job = scope.launch {
            block(jobState)
        }

        lock.withLock {
            jobMap[sessionId] = jobState.apply { this.job = job }
        }

        job.invokeOnCompletion {
            runBlocking {
                lock.withLock {
                    // Only remove if this exact JobState is still registered; also
                    // drop the LRU entry in the same branch so a stale handler can
                    // never strip a newer session's LRU position.
                    if (jobMap[sessionId] === jobState) {
                        jobMap.remove(sessionId)
                        lruList.remove(sessionId)
                    }
                }
            }
        }

        return job
    }

    suspend fun cancel(sessionId: String) {
        lock.withLock {
            jobMap[sessionId]?.job?.cancel()
            jobMap.remove(sessionId)
            lruList.remove(sessionId)
        }
    }

    fun getState(sessionId: String): JobState? = jobMap[sessionId]

    suspend fun cancelAll() {
        lock.withLock {
            jobMap.values.forEach { it.job.cancel() }
            jobMap.clear()
            lruList.clear()
        }
    }
}
