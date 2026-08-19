// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.utils

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 纯 Kotlin 数值/时间格式化工具。
 *
 * 替代 JVM 专属的 `String.format("%.1f")` 系列用法，
 * 保证 commonMain 在 Android / iOS 上同样可用。
 */

/** `12.345.toFixed(1) == "12.3"`（四舍五入，保留固定小数位）。 */
fun Double.toFixed(decimals: Int): String {
    val scaled = roundToLongWithDecimals(this, decimals)
    val negative = scaled < 0
    val absScaled = abs(scaled)
    var whole = absScaled / pow10(decimals)
    var frac = (absScaled % pow10(decimals)).toString().padStart(decimals + 1, '0')
    frac = frac.substring(0, decimals.coerceAtLeast(1))
    if (decimals == 0) return (if (negative) "-" else "") + whole.toString()
    return (if (negative) "-" else "") + whole + "." + frac
}

private fun roundToLongWithDecimals(value: Double, decimals: Int): Long {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    return (value * factor).roundToLong()
}

private fun pow10(n: Int): Long {
    var result = 1L
    repeat(n) { result *= 10 }
    return result
}

/** `5.toHex2() == "05"`（大写十六进制，两位）。 */
fun Int.toHex2(): String = (this and 0xFF).toString(16).padStart(2, '0').uppercase()

/** `7.toPad2() == "07"`（十进制两位补零）。 */
fun Int.toPad2(): String = toString().padStart(2, '0')

/** 带单位的字节大小（1 位小数）。 */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> (bytes / 1_048_576.0).toFixed(1) + " MB"
    bytes >= 1024 -> (bytes / 1024.0).toFixed(1) + " KB"
    else -> "${bytes}B"
}

/** 带单位的计数（1 位小数 + k）。 */
fun formatCount(n: Long): String = if (n >= 1000) (n / 1000.0).toFixed(1) + "k" else n.toString()

/** 带单位的耗时（2 位小数 + s）。 */
fun formatMillis(ms: Long): String = if (ms >= 1000) (ms / 1000.0).toFixed(2) + "s" else "${ms}ms"

/** 带单位的兆字节。 */
fun formatMegaBytes(mb: Double): String =
    if (mb == mb.roundToInt().toDouble()) "${mb.roundToInt()}MB" else mb.toFixed(2) + "MB"

// ═══════════════════════════════════════════════════════════
// 待办到期 / 事件时间（移动端卡片展示）
// ═══════════════════════════════════════════════════════════

private val WEEKDAY_CN = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 待办相对到期文案。
 *
 * - 已完成 → 「已完成」
 * - 到期日 = 今天 → 「今天 HH:mm」
 * - 到期日 = 明天 → 「明天」
 * - 2–6 天内 → 「周X」
 * - 已逾期 → 「已逾期」
 * - 其余 → 「M月D日」
 * - [dueAt] 为 null → 空串（无期限）
 *
 * @param zone 时区（默认系统时区）；测试可传固定时区保证确定性。
 */
fun formatDue(
    dueAt: Long?,
    completed: Boolean,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (completed) return "已完成"
    if (dueAt == null) return ""
    val dueDt = Instant.fromEpochMilliseconds(dueAt).toLocalDateTime(zone)
    val dueDate = dueDt.date
    val today = now.toLocalDateTime(zone).date
    val days = dueDate.toEpochDays() - today.toEpochDays()
    val hhmm = "${dueDt.hour.toPad2()}:${dueDt.minute.toPad2()}"
    return when {
        days < 0L -> "已逾期"
        days == 0L -> "今天 $hhmm"
        days == 1L -> "明天"
        days in 2L..6L -> "周${WEEKDAY_CN[dueDate.dayOfWeek.ordinal]}"
        else -> "${dueDate.monthNumber}月${dueDate.day}日"
    }
}

/**
 * 事件时间展示：全天 → 「全天」；有分钟数 → 「HH:mm」；未指定 → 空串。
 */
fun formatEventTime(allDay: Boolean, timeMinutes: Int?): String = when {
    allDay -> "全天"
    timeMinutes != null -> "${(timeMinutes / 60).toPad2()}:${(timeMinutes % 60).toPad2()}"
    else -> ""
}

// ═══════════════════════════════════════════════════════════
// 题目编号规范化（搜索框输入校验）
// ═══════════════════════════════════════════════════════════

/**
 * 洛谷题目编号校验 + 规范化。
 *
 * 规则（与洛谷常见编号格式对齐）：
 * - 去首尾空白、统一大写（洛谷 PID 区分大小写，官方均为大写）；
 * - 纯数字（如 `1000`）原样保留；
 * - 前缀字母 + 数字（如 `P1234`、`CF1234A`、`U1234`）保留；
 * - 其余（如 `P`、`Problem`、`Pxyz`、空串）返回 null，视为非法。
 *
 * @return 规范化后的编号；非法输入返回 null。
 */
fun normalizeProblemId(raw: String): String? {
    val pid = raw.trim().uppercase()
    if (pid.isEmpty()) return null
    // 长度上限（洛谷/CF 编号均在 12 字符内），防超长输入直达网络层
    if (pid.length > 12) return null
    if (pid.all { it in '0'..'9' }) return pid
    // 前缀字母 + 数字，允许末尾题目字母（如 CF1234A）
    return if (Regex("^[A-Z]{1,5}\\d+[A-Z]?$").matches(pid)) pid else null
}
