package com.github.hatoyuze.luogu.gui.platform

import kotlin.time.Clock

/**
 * Current epoch milliseconds, backed by [kotlin.time.Clock] (stable since
 * Kotlin 2.2). Used for DB timestamps, log entries and UI timers.
 */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
