// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.log

import com.github.hatoyuze.luogu.gui.platform.systemFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem
import okio.Path

/**
 * JSON Lines log sink with size-based rotation.
 *
 * Writes `app.log`; once it exceeds [maxBytes] it is rotated to `app.log.1`
 * (previous backup replaced). [readRecent] merges both files, newest first.
 */
class FileLogSink(
    private val dir: Path,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : LogSink {

    private val fileSystem: FileSystem = systemFileSystem
    private val json = Json { encodeDefaults = false }

    override val location: String get() = dir.toString()

    private val lock = Mutex()

    override fun write(entry: LogEntryData) = runBlocking {
        lock.withLock {
            fileSystem.createDirectories(dir, mustCreate = false)
            rotateIfNeeded()
            val sink = fileSystem.appendingSink(currentFile())
            try {
                val buffer = Buffer().apply {
                    writeUtf8(json.encodeToString(LogEntryData.serializer(), entry) + "\n")
                }
                sink.write(buffer, buffer.size)
            } finally {
                sink.close()
            }
        }
    }

    override fun readRecent(limit: Int, category: LogCategory?, level: LogLevel?): List<LogEntryData> = runBlocking {
        lock.withLock {
            if (!fileSystem.exists(dir)) return@withLock emptyList()
            val lines = mutableListOf<String>()
            backupFile().takeIf { fileSystem.exists(it) }?.let { lines.addAll(readFileLines(it)) }
            currentFile().takeIf { fileSystem.exists(it) }?.let { lines.addAll(readFileLines(it)) }
            lines.asReversed()
                .mapNotNull { line ->
                    runCatching { json.decodeFromString(LogEntryData.serializer(), line) }.getOrNull()
                }
                .filter { category == null || it.category == category }
                .filter { level == null || it.level == level }
                .take(limit)
        }
    }

    override fun clear() {
        runBlocking {
            lock.withLock {
                runCatching { fileSystem.delete(currentFile()) }
                runCatching { fileSystem.delete(backupFile()) }
            }
        }
    }

    private fun readFileLines(path: Path): List<String> {
        val source = fileSystem.source(path)
        try {
            val buffer = Buffer()
            buffer.writeAll(source)
            return buffer.readUtf8().lines()
        } finally {
            source.close()
        }
    }

    private fun rotateIfNeeded() {
        val current = currentFile()
        if (!fileSystem.exists(current)) return
        val currentSize = fileSystem.metadataOrNull(current)?.size ?: return
        if (currentSize <= maxBytes) return
        val backup = backupFile()
        runCatching { fileSystem.delete(backup) }
        runCatching { fileSystem.atomicMove(current, backup) }
    }

    private fun currentFile(): Path = dir / "app.log"
    private fun backupFile(): Path = dir / "app.log.1"

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 5 * 1024 * 1024
    }
}
