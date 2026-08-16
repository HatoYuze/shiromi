package com.github.hatoyuze.luogu.skill

import com.github.hatoyuze.luogu.skill.api.DifficultyLevel
import com.github.hatoyuze.luogu.skill.api.LuoguApi
import com.github.hatoyuze.luogu.skill.api.LuoguApiException
import com.github.hatoyuze.luogu.skill.api.LuoguHttpClient
import com.github.hatoyuze.luogu.skill.api.LuoguTags
import com.github.hatoyuze.luogu.skill.api.ProblemType
import com.github.hatoyuze.luogu.skill.api.installLuoguTools
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LuoguApiTest {

    // ═══════════════ LuoguTags ═══════════════

    @Test fun `resolveTags returns correct names for known IDs`() {
        val r = LuoguTags.resolveTags(listOf(1, 2))
        assertEquals(2, r.size)
        assertEquals("模拟", r[0].name)
        assertEquals("字符串", r[1].name)
    }
    @Test fun `resolveTags skips unknown IDs`() {
        assertEquals(2, LuoguTags.resolveTags(listOf(1, 99999, 2)).size)
    }
    @Test fun `resolveTags empty returns empty`() {
        assertEquals(0, LuoguTags.resolveTags(emptyList()).size)
    }
    @Test fun `resolveTag unknown returns null`() {
        assertNull(LuoguTags.resolveTag(99999))
    }
    @Test fun `allTags sorted by ID`() {
        val all = LuoguTags.allTags()
        assertTrue(all.size >= 100)
    }
    @Test fun `allTags returns expected count`() {
        val all = LuoguTags.allTags()
        assertTrue(all.isNotEmpty(), "Tag list should not be empty")
    }

    // ═══════════════ DifficultyLevel ═══════════════

    @Test fun `fromId correct`() {
        assertEquals(DifficultyLevel.ENTRY, DifficultyLevel.fromId(1))
        assertEquals(DifficultyLevel.PROVINCIAL, DifficultyLevel.fromId(6))
    }
    @Test fun `fromId unknown returns UNRATED`() {
        assertEquals(DifficultyLevel.UNRATED, DifficultyLevel.fromId(999))
    }

    // ═══════════════ ProblemType ═══════════════

    @Test fun `fromValue correct`() {
        assertEquals(ProblemType.CF, ProblemType.fromValue("cf"))
        assertEquals(ProblemType.AT, ProblemType.fromValue("at"))
    }
    @Test fun `fromValue unknown returns LUOGU`() {
        assertEquals(ProblemType.LUOGU, ProblemType.fromValue("xxx"))
    }

    // ═══════════════ HTTP error handling ═══════════════

    @Test fun `missing credentials ok for tool reg`() = runTest {
        val host = io.github.hatoyuze.deepseek.toolcall.dsl.toolHost { installLuoguTools(LuoguApi()) }
        assertEquals(9, host.getDefinitions().size)
    }
    @Test fun `cookie seeding works`() = runTest {
        val api = mockApi { respondOk(jsonOkEmpty) }
        api.cookie = "_uid=0; __client_id=test123; C3VK=abc"
        // Should not throw — cookie is seeded and request succeeds
        val r = api.searchProblems()
        assertEquals(200, r.status)
    }
    @Test fun `403 throws csrf error`() = runTest {
        val api = mockApi { respondError(HttpStatusCode.Forbidden) }
        api.apply { cookie = "c" }
        val ex = assertFailsWith<LuoguApiException> { api.searchProblems() }
        assertTrue(ex.message!!.contains("被拒绝") || ex.message!!.contains("Cookie"))
    }
    @Test fun `404 throws not found`() = runTest {
        val api = mockApi { respondError(HttpStatusCode.NotFound) }
        api.apply { cookie = "c" }
        val ex = assertFailsWith<LuoguApiException> { api.getProblemDetail("P999999") }
        assertTrue(ex.message!!.contains("不存在"))
    }
    @Test fun `500 throws server error`() = runTest {
        val api = mockApi { respondError(HttpStatusCode.InternalServerError) }
        api.apply { cookie = "c" }
        val ex = assertFailsWith<LuoguApiException> { api.searchProblems() }
        assertTrue(ex.message!!.contains("服务器错误"))
    }
    @Test fun `429 throws rate limit`() = runTest {
        val api = mockApi { respondError(HttpStatusCode.TooManyRequests) }
        api.apply { cookie = "c" }
        val ex = assertFailsWith<LuoguApiException> { api.searchProblems() }
        assertTrue(ex.message!!.contains("频率过高"))
    }

    // ═══════════════ URL building ═══════════════

    @Test fun `search URL includes all params`() = runTest {
        var url = ""
        val api = mockApi { req -> url = req.url.toString(); respondOk(jsonOkEmpty) }
        api.apply { cookie = "c" }
        api.searchProblems(keyword = "最短路", tags = listOf(6, 7), page = 2)
        assertTrue(url.contains("keyword=") && url.contains("page=2"))
    }
    @Test fun `getProblemDetail uppercases pid`() = runTest {
        var path = ""
        val api = mockApi { req -> path = req.url.encodedPath; respondError(HttpStatusCode.NotFound) }
        api.apply { cookie = "c" }
        assertFailsWith<LuoguApiException> { api.getProblemDetail("p1000") }
        assertEquals("/problem/P1000", path)
    }

    // ═══════════════ Tool registration ═══════════════

    @Test fun `installLuoguTools registers 9 tools`() {
        val h = io.github.hatoyuze.deepseek.toolcall.dsl.toolHost { installLuoguTools(LuoguApi()) }
        assertEquals(9, h.getDefinitions().size)
    }
    @Test fun `correct tool names`() {
        val h = io.github.hatoyuze.deepseek.toolcall.dsl.toolHost { installLuoguTools(LuoguApi()) }
        val n = h.getDefinitions().map { it.name }.toSet()
        assertTrue(n.containsAll(listOf("luogu_get_filters", "luogu_search_problems", "luogu_get_problem", "luogu_get_solutions", "luogu_search_trainings", "luogu_get_training", "luogu_get_practice", "luogu_get_records", "luogu_get_record")))
    }

    // ═══════════════ Cache ═══════════════

    @Test fun `cache saves and loads problem`() = runTest {
        val cache = MemoryCacheStorage()
        val api = mockApi(cache) { respondOk(validProblemJson) }
        api.cookie = "c"
        api.getProblemDetail("P1000")
        val r2 = api.getProblemDetail("P1000")
        assertEquals(200, r2.status)
        assertTrue(cache.entries.keys.any { it.startsWith("problem_P1000") })
    }
    @Test fun `cache disabled does not write`() = runTest {
        val api = mockApi { respondOk(validProblemJson) }
        api.cookie = "c"
        val r = api.getProblemDetail("P1000")
        assertEquals(200, r.status)
    }

    // ═══════════════ Response parsing ═══════════════

    @Test fun `searchProblems parses valid response`() = runTest {
        val api = mockApi { respondOk(validListJson) }
        api.apply { cookie = "c" }
        val r = api.searchProblems(keyword = "A+B")
        assertEquals(200, r.status)
        assertEquals("P1000", r.data.problems.result.first().pid)
    }
    @Test fun `getSolutions parses valid response`() = runTest {
        val api = mockApi { respondOk(validSolutionJson) }
        api.apply { cookie = "c" }
        val r = api.getSolutions("P1000")
        assertEquals(200, r.status)
        assertEquals("题解达人", r.data.solutions.result.first().author.name)
    }

    // ═══════════════ Pass rate ═══════════════

    @Test fun `passRate helpers`() {
        assertEquals("N/A", calcPassRateHelper(0, 0))
        assertEquals("100.0%", calcPassRateHelper(100, 100))
        assertEquals("50.0%", calcPassRateHelper(250, 500))
    }

    companion object {
        private const val jsonOkEmpty = """{"status":200,"data":{"problems":{"perPage":50,"count":0,"result":[]},"filter":{}}}"""
        private val validListJson = """{"status":200,"data":{"problems":{"perPage":50,"count":1,"result":[{"pid":"P1000","type":"P","name":"A+B Problem","difficulty":1,"tags":[1,5],"totalSubmit":500,"totalAccepted":250,"flag":0}]},"filter":{"keyword":"A+B","type":"luogu","page":1}}}"""
        private val validSolutionJson = """{"status":200,"data":{"solutions":{"perPage":20,"count":45,"result":[{"sid":1001,"author":{"uid":500,"name":"题解达人","isAdmin":false},"title":"使用高精度加法","content":"## 解题思路\n\n内容"}]}}}"""
        private val validProblemJson = """{"status":200,"data":{"problem":{"pid":"P1000","name":"A+B Problem","description":"## 题目描述\n\n输入两个整数 a, b，输出它们的和。","tags":[1,5],"difficulty":1,"totalSubmit":50000,"totalAccepted":25000}}}"""
    }
}

@Suppress("UNCHECKED_CAST")
private fun mockApi(handler: MockRequestHandler): LuoguApi {
    val mockClient = HttpClient(MockEngine(handler))
    val http = LuoguHttpClient(mockClient)
    return LuoguApi(http)
}
private fun mockApi(cache: com.github.hatoyuze.luogu.skill.cache.CacheStorage, handler: MockRequestHandler): LuoguApi {
    val mockClient = HttpClient(MockEngine(handler))
    val http = LuoguHttpClient(mockClient)
    return LuoguApi(http, cache)
}

/** In-memory CacheStorage fake replacing the deleted FileCacheStorage in tests. */
class MemoryCacheStorage : com.github.hatoyuze.luogu.skill.cache.CacheStorage {
    val entries = LinkedHashMap<String, ByteArray>()
    override suspend fun read(key: String): ByteArray? = entries[key]
    override suspend fun write(key: String, data: ByteArray, ttlMs: Long?) {
        if (data.size <= 512 * 1024) entries[key] = data
    }
    override suspend fun delete(key: String) { entries.remove(key) }
    override suspend fun listEntries(): List<com.github.hatoyuze.luogu.skill.cache.CacheEntry> =
        entries.map { com.github.hatoyuze.luogu.skill.cache.CacheEntry(it.key, it.value.size.toLong(), 0L) }
}
private fun calcPassRateHelper(accepted: Int, total: Int): String {
    if (total <= 0) return "N/A"
    val rate = accepted * 100.0 / total
    return "${(rate * 10.0).toInt() / 10.0}%"
}
