// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

// ═══════════════════════════════════════════════════════════
// Chinese date formatting helpers
// ═══════════════════════════════════════════════════════════

private val MONTH_NAMES = listOf(
    "", "一月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "十一月", "十二月"
)

private val WEEKDAY_NAMES = listOf(
    "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"
)

fun formatMonthChinese(date: LocalDate): String = MONTH_NAMES[date.month.number]

fun formatWeekdayChinese(date: LocalDate): String {
    // kotlinx-datetime: Monday=0
    return WEEKDAY_NAMES[date.dayOfWeek.ordinal]
}

/** 短星期：星期六 → 周六（用于日期横幅等紧凑场景）。 */
fun formatWeekdayShortChinese(date: LocalDate): String =
    "周" + formatWeekdayChinese(date).removePrefix("星期")
