@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package com.github.hatoyuze.luogu.gui.data.log

import java.io.File
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileLogSinkTest {

    private fun entry(seq: Int, category: LogCategory = LogCategory.APP) = LogEntryData(
        timestamp = seq.toLong(),
        level = LogLevel.INFO,
        category = category,
        event = "test.$seq",
        message = "message $seq",
    )

    @Test
    fun writeAndReadRecent_shouldReturnNewestFirst() {
        val dir = Files.createTempDirectory("logsink-test")
        try {
            val sink = FileLogSink(dir.toFile().absolutePath.toPath())
            (1..5).forEach { sink.write(entry(it)) }

            val recent = sink.readRecent(3)
            assertEquals(listOf(5L, 4L, 3L), recent.map { it.timestamp })
            assertEquals("message 5", recent.first().message)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun write_shouldRotateWhenExceedingMaxBytes() {
        val dir = Files.createTempDirectory("logsink-rotate")
        try {
            val sink = FileLogSink(dir.toFile().absolutePath.toPath(), maxBytes = 200)
            // Entries are ~90 bytes each; ~3 lines should exceed 200 bytes and rotate.
            (1..10).forEach { sink.write(entry(it)) }

            assertTrue(File(dir.toFile(), "app.log").exists())
            assertTrue(File(dir.toFile(), "app.log.1").exists())
            val recent = sink.readRecent(100)
            assertTrue(recent.isNotEmpty())
            // Rotation keeps the newest entries; old backups are overwritten by design.
            assertEquals(10L, recent.maxOf { it.timestamp })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readRecent_shouldFilterByCategory() {
        val dir = Files.createTempDirectory("logsink-filter")
        try {
            val sink = FileLogSink(dir.toFile().absolutePath.toPath())
            sink.write(entry(1, LogCategory.APP))
            sink.write(entry(2, LogCategory.HTTP))
            sink.write(entry(3, LogCategory.APP))

            assertEquals(listOf(3L, 1L), sink.readRecent(10, category = LogCategory.APP).map { it.timestamp })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun clear_shouldRemoveFiles() {
        val dir = Files.createTempDirectory("logsink-clear")
        try {
            val sink = FileLogSink(dir.toFile().absolutePath.toPath())
            sink.write(entry(1))
            sink.clear()
            assertTrue(sink.readRecent(10).isEmpty())
            assertTrue(!File(dir.toFile(), "app.log").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
