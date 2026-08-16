package com.github.hatoyuze.luogu.skill.api

import io.github.hatoyuze.deepseek.toolcall.executor.ParameterBag
import io.github.hatoyuze.deepseek.toolcall.dsl.ToolHostBuilder

fun ToolHostBuilder.installLuoguTools(api: LuoguApiClient) {

    tool("luogu_get_filters") {
        description = "获取洛谷题目搜索的可用筛选条件（算法标签、难度区间、排序方式）"
        parameters {}
        execute { _, _ -> getFiltersImpl() }
    }

    tool("luogu_search_problems") {
        description = buildString {
            append("在洛谷题库中搜索题目。内置搜索仅支持关键词/标题匹配，建议优先用 tag + difficulty 筛选。")
            append("每次返回一页（默认10条），Agent 自行管理分页。")
        }
        parameters {
            string("keyword") { description = "题目标题或描述关键词"; required = false }
            string("tag") { description = "算法标签 ID（来自 luogu_get_filters）"; required = false }
            integer("difficulty_min") { description = "难度下限 1~8"; minimum = 1; maximum = 8; required = false }
            integer("difficulty_max") { description = "难度上限 1~8"; minimum = 1; maximum = 8; required = false }
            string("sort_by") { description = "排序方式: relevance/difficulty_asc/difficulty_desc/pass_rate"; required = false }
            integer("page") { description = "页码，默认1"; minimum = 1; required = false }
            integer("page_size") { description = "每页条数，默认10，上限15"; minimum = 1; maximum = 15; required = false }
        }
        execute { bag, _ -> searchProblemsImpl(api, bag) }
    }

    tool("luogu_get_problem") {
        description = "获取一道题目的完整内容（描述、输入输出格式、样例、数据范围）"
        parameters {
            string("pid") { description = "洛谷题号，如 P5470"; required = true }
        }
        execute { bag, _ -> getProblemImpl(api, bag) }
    }

    tool("luogu_get_solutions") {
        description = buildString {
            append("获取题解列表。支持两种模式：")
            append("1) 快速扫描 — 指定 limit+truncate 返回截断预览；")
            append("2) 深度精读 — 指定 solution_ids 获取指定篇目全文。")
        }
        parameters {
            string("pid") { description = "洛谷题号"; required = true }
            integer("limit") { description = "最多返回篇数，默认10"; minimum = 1; required = false }
            integer("truncate") { description = "每篇仅返回前N行。未指定时返回全文"; minimum = 1; required = false }
            array("solution_ids") { description = "指定题解ID列表，存在时忽略limit和truncate"; items { string("id") {} } }
        }
        execute { bag, _ -> getSolutionsImpl(api, bag) }
    }

    tool("luogu_search_trainings") {
        description = buildString {
            append("搜索洛谷题单（训练计划）。返回题单摘要列表，含名称、题目数量、收藏数。")
            append("Agent 选题时可先用此工具发现合适的训练题单，再通过 luogu_get_training 获取详情。")
        }
        parameters {
            string("keyword") { description = "搜索关键词（匹配题单名称）"; required = false }
            string("type") { description = "题单类型，默认 select"; required = false }
            integer("page") { description = "页码，默认1"; minimum = 1; required = false }
        }
        execute { bag, _ -> searchTrainingsImpl(api, bag) }
    }

    tool("luogu_get_training") {
        description = buildString {
            append("获取指定题单的完整详情。返回题单描述、题目列表（如有）、")
            append("以及描述中引用的子题单 ID（linked_training_ids）。")
            append("若 problem_count=0，说明题目通过 linked_training_ids 中的题单间接提供，")
            append("Agent 可继续调用 luogu_get_training 获取子题单内容。")
        }
        parameters {
            integer("id") { description = "题单 ID（来自 luogu_search_trainings 或 linked_training_ids）"; required = true }
        }
        execute { bag, _ -> getTrainingImpl(api, bag) }
    }

    tool("luogu_get_practice") {
        description = buildString {
            append("获取用户的洛谷做题记录（已通过 passed + 已尝试 submitted + 用户信息 user）。")
            append("UID 自动从 cookie 的 _uid 字段解析，也可通过 LuoguApi.uid0 显式配置。")
            append("Agent 可在选题阶段用此工具避重——passed 中的题号不应再次选取。")
        }
        parameters {}
        execute { _, _ -> getPracticeImpl(api) }
    }

    tool("luogu_get_records") {
        description = buildString {
            append("获取用户在指定题目上的提交记录列表。返回每次提交的ID、分数、状态、时空消耗、代码长度。")
            append("默认返回最近 5 条。Agent 可据此判断学生是逐步逼近还是反复试错。")
        }
        parameters {
            string("pid") { description = "洛谷题号"; required = true }
            integer("limit") { description = "最多返回条数，默认5"; minimum = 1; maximum = 20; required = false }
        }
        execute { bag, _ -> getRecordsImpl(api, bag) }
    }

    tool("luogu_get_record") {
        description = "获取指定提交记录的完整详情，包括源代码。Agent 可在学生卡住时查看实际代码以定位错误。"
        parameters {
            integer("id") { description = "提交记录 ID（来自 luogu_get_records）"; required = true }
        }
        execute { bag, _ -> getRecordImpl(api, bag) }
    }
}

// ═══════════════════════════════════════════════════════════
// Impl
// ═══════════════════════════════════════════════════════════

private fun getFiltersImpl(): FilterMetadata {
    val levels = DifficultyLevel.entries
        .filter { it.id in 1..8 }
        .associate { it.id.toString() to it.label }
    return FilterMetadata(
        tags = LuoguTags.allTags(),
        difficultyRange = DifficultyRange(min = 1, max = 8, levels = levels),
        sortOptions = listOf("relevance", "difficulty_asc", "difficulty_desc", "pass_rate"),
    )
}

private suspend fun searchProblemsImpl(api: LuoguApiClient, bag: ParameterBag): SearchResult {
    val keyword = bag.getStringOrNull("keyword")
    val tag = bag["tag"]?.toString()?.toDoubleOrNull()?.toInt()
    val page = bag["page"]?.toString()?.toDoubleOrNull()?.toInt() ?: 1
    val pageSize = bag["page_size"]?.toString()?.toDoubleOrNull()?.toInt() ?: 10

    val r = api.searchProblems(
        keyword = keyword,
        difficultyMin = bag["difficulty_min"]?.toString()?.toDoubleOrNull()?.toInt(),
        difficultyMax = bag["difficulty_max"]?.toString()?.toDoubleOrNull()?.toInt(),
        tags = if (tag != null) listOf(tag) else null,
        page = page,
    )
    val data = r.data
    return SearchResult(
        total = data.problems.count,
        page = page,
        pageSize = pageSize,
        problems = data.problems.result.take(pageSize).map { it.toSearchItem() },
    )
}

private suspend fun getProblemImpl(api: LuoguApiClient, bag: ParameterBag): ProblemInfo {
    val pid = bag.getString("pid")
    val pr = api.getProblemDetail(pid).data.problem
    val resolvedTags = LuoguTags.resolveTags(pr.tags)
    val passRate = if (pr.totalSubmit > 0) pr.totalAccepted.toDouble() / pr.totalSubmit else 0.0
    val scores = calcDifficultyScores(pr.difficulty, passRate, pr.totalSubmit, resolvedTags)
    return ProblemInfo(
        pid = pr.pid,
        title = pr.name,
        difficulty = pr.difficulty,
        difficultyName = DifficultyLevel.fromId(pr.difficulty).label,
        tags = resolvedTags.map { it.name },
        description = pr.description,
        inputFormat = pr.inputFormat,
        outputFormat = pr.outputFormat,
        samples = pr.samples,
        constraints = ProblemConstraints(
            timeLimit = pr.timeLimit?.let { "${it / 1000.0}s" },
            memoryLimit = pr.memoryLimit?.let { formatMegaBytes(it / 1024.0) },
        ),
        difficultyScores = DifficultyScoresInfo(scores.overall, scores.knowledge, scores.thinking),
    )
}

private suspend fun getSolutionsImpl(api: LuoguApiClient, bag: ParameterBag): SolutionInfo {
    val pid = bag.getString("pid")
    val limit = bag["limit"]?.toString()?.toDoubleOrNull()?.toInt() ?: 10
    val truncateLines = bag["truncate"]?.toString()?.toDoubleOrNull()?.toInt()
    val ids = try { bag.getList("solution_ids").mapNotNull { it?.toString() } } catch (_: Exception) { emptyList() }

    val data = api.getSolutions(pid).data
    val allSolutions = data.solutions.result.map { sol ->
        SolutionItem(
            id = "sol_${sol.sid}",
            author = sol.author.name,
            title = sol.title,
            voteCount = sol.likes ?: 0,
            content = sol.content,
        )
    }

    val selected = if (ids.isNotEmpty()) allSolutions.filter { it.id in ids }
                   else allSolutions.take(limit)

    val truncated = truncateLines != null && ids.isEmpty()
    val final = if (truncated) selected.map { s -> s.copy(content = s.content.lines().take(truncateLines).joinToString("\n")) }
                else selected

    return SolutionInfo(
        pid = pid,
        totalSolutions = data.solutions.count,
        returned = final.size,
        truncated = truncated,
        solutions = final,
    )
}

private suspend fun searchTrainingsImpl(api: LuoguApiClient, bag: ParameterBag): TrainingSearchResult {
    val keyword = bag.getStringOrNull("keyword")
    val type = bag.getStringOrNull("type") ?: "select"
    val page = bag["page"]?.toString()?.toDoubleOrNull()?.toInt() ?: 1

    val data = api.searchTrainings(keyword = keyword, type = type, page = page).data
    return TrainingSearchResult(
        total = data.trainings.count,
        page = page,
        pageSize = data.trainings.perPage,
        trainings = data.trainings.result.map { t ->
            TrainingItem(id = t.id, name = t.name,
                problemCount = t.problemCount, markCount = t.markCount,
                provider = t.provider?.name)
        },
        categories = data.categories,
    )
}

private suspend fun getTrainingImpl(api: LuoguApiClient, bag: ParameterBag): TrainingInfo {
    val id = bag["id"]?.toString()?.toDoubleOrNull()?.toInt()
        ?: throw IllegalArgumentException("缺少题单 ID")
    val t = api.getTrainingDetail(id).data.training
    val linkedIds = Regex("""luogu\.com\.cn/training/(\d+)""")
        .findAll(t.description)
        .map { it.groupValues[1].toInt() }
        .filter { it != id }
        .toSet().toList()

    return TrainingInfo(
        id = t.id, name = t.name, description = t.description,
        problemCount = t.problemCount, markCount = t.markCount,
        problems = t.problems.map { it.toSearchItem() },
        linkedTrainingIds = linkedIds,
    )
}

private suspend fun getPracticeImpl(api: LuoguApiClient): PracticeResult {
    val uid = api.uid
    val r = api.getPractice(uid)
    return PracticeResult(
        uid = uid,
        passedCount = r.passed.size,
        submittedCount = r.submitted.size,
        passed = r.passed.map { it.toSearchItem(passRate = 1.0) },
        submitted = r.submitted.map { it.toSearchItem(passRate = 0.0) },
    )
}

private suspend fun getRecordsImpl(api: LuoguApiClient, bag: ParameterBag): RecordsResult {
    val pid = bag.getString("pid")
    val limit = bag["limit"]?.toString()?.toDoubleOrNull()?.toInt() ?: 5
    val uid = api.uid
    val r = api.getRecordList(pid, uid)
    return RecordsResult(
        pid = pid, uid = uid, totalCount = r.count,
        records = r.result.take(limit).map {
            RecordSummaryItem(
                id = it.id, score = it.score, status = it.status,
                timeMs = it.time, memoryKb = it.memory,
                codeLength = it.sourceCodeLength, submitTime = it.submitTime,
                language = it.language,
            )
        },
    )
}

private suspend fun getRecordImpl(api: LuoguApiClient, bag: ParameterBag): RecordDetailInfo {
    val id = bag["id"]?.toString()?.toDoubleOrNull()?.toLong()
        ?: throw IllegalArgumentException("缺少记录 ID")
    val d = api.getRecordDetail(id)
    return RecordDetailInfo(
        id = d.id,
        pid = d.problem?.pid ?: "",
        uid = api.uid,
        score = d.score,
        status = d.status,
        timeMs = d.time,
        memoryKb = d.memory,
        codeLength = d.sourceCodeLength,
        submitTime = d.submitTime,
        language = d.language,
        sourceCode = d.sourceCode,
        judgeResult = d.detail?.judgeResult,
    )
}

// ═══════════════════════════════════════════════════════════
// 映射辅助
// ═══════════════════════════════════════════════════════════

private fun ProblemSummary.toSearchItem() = SearchProblemItem(
    pid = pid, title = name,
    difficulty = difficulty,
    difficultyName = DifficultyLevel.fromId(difficulty).label,
    tags = LuoguTags.resolveTags(tags).map { it.name },
    passRate = if (totalSubmit > 0) (totalAccepted * 100.0 / totalSubmit).let { (it * 100.0).toInt() / 10000.0 } else 0.0,
    totalSubmissions = totalSubmit,
)

private fun PassedProblem.toSearchItem(passRate: Double) = SearchProblemItem(
    pid = pid, title = name,
    difficulty = difficulty,
    difficultyName = DifficultyLevel.fromId(difficulty).label,
    tags = emptyList(),
    passRate = passRate,
    totalSubmissions = 0,
)

/** Pure-Kotlin two-decimal formatting (String.format is JVM-only). */
internal fun formatMegaBytes(value: Double): String {
    val rounded = (value * 100).toLong() / 100.0
    val whole = rounded.toLong()
    val frac = ((rounded - whole) * 100 + 0.5).toInt().coerceIn(0, 99)
    return "${whole}.${frac.toString().padStart(2, '0')}MB"
}
