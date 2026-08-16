package com.github.hatoyuze.luogu.skill.cache

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * 洛谷 API 响应的本地持久化缓存。
 *
 * 将数据以 gzip 压缩 JSON 形式存入 [storage]，减少对洛谷服务器的重复请求。
 * 缓存键自动转换为安全文件名格式，存储压力管理由 [CacheStorage] 实现负责。
 */
internal class LuoguCache(
    private val storage: CacheStorage,
) {

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    // ── 公开 API ──

    /**
     * 从缓存加载对象。
     *
     * @param key 缓存键（不含扩展名）
     * @return 反序列化的对象，缓存不存在或读取失败时返回 `null`
     */
    suspend inline fun <reified T> load(key: String): T? {
        return try {
            val bytes = storage.read(key) ?: return null
            json.decodeFromString<T>(bytes.decodeToString())
        } catch (_: Exception) {
            // 缓存损坏则删除，下次重新请求
            storage.delete(key)
            null
        }
    }

    /**
     * 将对象以 gzip 压缩 JSON 形式存入缓存。
     *
     * @param key 缓存键
     * @param value 要缓存的对象（需可序列化）
     */
    suspend inline fun <reified T> save(key: String, value: T, ttlMs: Long? = null) {
        try {
            val bytes = json.encodeToString(serializer<T>(), value).encodeToByteArray()
            storage.write(key, bytes, ttlMs)
        } catch (_: Exception) {
            // 缓存写入失败不影响主流程
        }
    }
}
