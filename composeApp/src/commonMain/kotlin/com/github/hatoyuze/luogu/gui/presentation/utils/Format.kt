package com.github.hatoyuze.luogu.gui.presentation.utils

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 纯 Kotlin 数值格式化工具。
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
