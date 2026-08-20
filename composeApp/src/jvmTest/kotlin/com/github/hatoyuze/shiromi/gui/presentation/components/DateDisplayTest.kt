// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class DateDisplayTest {
    @Test
    fun formatWeekdayShortChinese_mapsWeekdayToShortForm() {
        // 2026-08-01 为周六，因此 8/3 周一、8/6 周四、8/16 周日
        assertEquals("周一", formatWeekdayShortChinese(LocalDate(2026, 8, 3)))
        assertEquals("周四", formatWeekdayShortChinese(LocalDate(2026, 8, 6)))
        assertEquals("周六", formatWeekdayShortChinese(LocalDate(2026, 8, 1)))
        assertEquals("周日", formatWeekdayShortChinese(LocalDate(2026, 8, 16)))
    }

    @Test
    fun formatMonthChinese_usesChineseMonthName() {
        assertEquals("八月", formatMonthChinese(LocalDate(2026, 8, 16)))
        assertEquals("一月", formatMonthChinese(LocalDate(2026, 1, 1)))
        assertEquals("十二月", formatMonthChinese(LocalDate(2026, 12, 31)))
    }
}
