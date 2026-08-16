package com.github.hatoyuze.luogu.gui.data.log

/** In-memory [LogSink] for tests. */
class TestLogSink : LogSink {
    val entries = mutableListOf<LogEntryData>()

    override fun write(entry: LogEntryData) {
        entries.add(entry)
    }

    override fun readRecent(limit: Int, category: LogCategory?, level: LogLevel?): List<LogEntryData> =
        entries.asReversed()
            .filter { category == null || it.category == category }
            .filter { level == null || it.level == level }
            .take(limit)

    override fun clear() {
        entries.clear()
    }

    override val location: String = "test"
}
