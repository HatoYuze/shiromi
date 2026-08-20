// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.utils

import com.github.hatoyuze.shiromi.gui.presentation.components.parseTimeMinutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * formatDue / formatEventTime 的确定性测试（固定 UTC 时区与基准时刻）。
 *
 * 基准：2026-08-16T12:00Z（UTC 周日）。
 */
class FormatTest {

    private val zone = TimeZone.UTC
    private val now = LocalDateTime(2026, 8, 16, 12, 0).toInstant(zone)

    private fun due(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    @Test
    fun formatDue_noDueAt_isEmpty() {
        assertEquals("", formatDue(null, completed = false, now = now, zone = zone))
    }

    @Test
    fun formatDue_completed_ignoresDue() {
        assertEquals("已完成", formatDue(due(2026, 8, 20, 9, 0), completed = true, now = now, zone = zone))
    }

    @Test
    fun formatDue_today_showsTime() {
        assertEquals("今天 18:30", formatDue(due(2026, 8, 16, 18, 30), completed = false, now = now, zone = zone))
    }

    @Test
    fun formatDue_tomorrow() {
        assertEquals("明天", formatDue(due(2026, 8, 17, 9, 0), completed = false, now = now, zone = zone))
    }

    @Test
    fun formatDue_withinWeek_showsWeekday() {
        // 2026-08-19 = 周三
        assertEquals("周三", formatDue(due(2026, 8, 19, 12, 0), completed = false, now = now, zone = zone))
    }

    @Test
    fun formatDue_day2_showsWeekday() {
        // 2026-08-18 = 周二（days == 2 边界）
        assertEquals("周二", formatDue(due(2026, 8, 18, 12, 0), completed = false, now = now, zone = zone))
    }

    @Test
    fun formatDue_day6_showsWeekday() {
        // 2026-08-22 = 周六（days == 6 边界）
        assertEquals("周六", formatDue(due(2026, 8, 22, 12, 0), completed = false, now = now, zone = zone))
    }

    @Test
    fun formatDue_day7_showsMonthDay() {
        // 2026-08-23（days == 7，超出周X范围）
        assertEquals("8月23日", formatDue(due(2026, 8, 23, 12, 0), completed = false, now = now, zone = zone))
    }

    @Test
    fun formatDue_overdue() {
        assertEquals("已逾期", formatDue(due(2026, 8, 10, 12, 0), completed = false, now = now, zone = zone))
    }

    @Test
    fun formatDue_farDate_showsMonthDay() {
        assertEquals("9月20日", formatDue(due(2026, 9, 20, 12, 0), completed = false, now = now, zone = zone))
    }

    @Test
    fun formatEventTime_allDay() {
        assertEquals("全天", formatEventTime(allDay = true, timeMinutes = null))
        assertEquals("全天", formatEventTime(allDay = true, timeMinutes = 780))
    }

    @Test
    fun formatEventTime_minutesToHhMm() {
        assertEquals("13:00", formatEventTime(allDay = false, timeMinutes = 13 * 60))
        assertEquals("00:00", formatEventTime(allDay = false, timeMinutes = 0))
        assertEquals("23:59", formatEventTime(allDay = false, timeMinutes = 23 * 60 + 59))
    }

    @Test
    fun formatEventTime_unspecified() {
        assertEquals("", formatEventTime(allDay = false, timeMinutes = null))
    }

    // ── parseTimeMinutes（EventEditDialog 时间校验）──

    @Test
    fun parseTimeMinutes_valid() {
        assertEquals(780, parseTimeMinutes("13:00"))
        assertEquals(5, parseTimeMinutes("0:05"))
        assertEquals(1439, parseTimeMinutes("23:59"))
    }

    @Test
    fun parseTimeMinutes_invalid() {
        assertNull(parseTimeMinutes("25:00"))
        assertNull(parseTimeMinutes("12:70"))
        assertNull(parseTimeMinutes(":30"))
        assertNull(parseTimeMinutes("abc"))
        assertNull(parseTimeMinutes(""))
    }
}
