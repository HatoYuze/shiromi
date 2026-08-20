// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.protocol.platform

/**
 * 读取内嵌的洛谷标签库 JSON。
 *
 * 标签数据以 Kotlin 常量内嵌（见 [LUOGU_TAGS_JSON]），
 * 所有 KMP 目标（JVM / Android / iOS）共用同一份数据。
 */
fun loadTagResourceJson(): String? = LUOGU_TAGS_JSON
