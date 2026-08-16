package com.github.hatoyuze.luogu.skill.platform

/**
 * 读取内嵌的洛谷标签库 JSON。
 *
 * 标签数据以 Kotlin 常量内嵌（见 [LUOGU_TAGS_JSON]），
 * 所有 KMP 目标（JVM / Android / iOS）共用同一份数据。
 */
fun loadTagResourceJson(): String? = LUOGU_TAGS_JSON
