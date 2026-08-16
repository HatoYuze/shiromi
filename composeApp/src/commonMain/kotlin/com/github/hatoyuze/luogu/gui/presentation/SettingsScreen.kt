@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    io.github.hatoyuze.deepseek.protocol.api.ExperimentalDeepseekApi::class,
)

package com.github.hatoyuze.luogu.gui.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.config.AppConfigStore
import com.github.hatoyuze.luogu.gui.config.ConfigService
import com.github.hatoyuze.luogu.gui.data.Logger
import com.github.hatoyuze.luogu.gui.data.log.AssistantMessageDetail
import com.github.hatoyuze.luogu.gui.data.log.HttpLogDetail
import com.github.hatoyuze.luogu.gui.data.log.LogCategory
import com.github.hatoyuze.luogu.gui.data.log.LogDetail
import com.github.hatoyuze.luogu.gui.data.log.LogEntryData
import com.github.hatoyuze.luogu.gui.data.log.LogLevel
import com.github.hatoyuze.luogu.gui.data.local.GlobalCacheStats
import com.github.hatoyuze.luogu.gui.data.local.LuoguCacheManager
import com.github.hatoyuze.luogu.gui.domain.chat.ChatService
import com.github.hatoyuze.luogu.gui.platform.openDirectory
import io.github.hatoyuze.deepseek.protocol.api.entity.ReasoningEffort
import io.github.hatoyuze.deepseek.protocol.api.entity.ThinkingMode
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.github.hatoyuze.luogu.gui.presentation.utils.toFixed

private val EFFORT_OPTIONS = listOf("AUTO", "HIGH", "MAX")

private fun thinkingModeToIndex(mode: ThinkingMode?): Int = when (mode) {
    is ThinkingMode.WithEffort -> if (mode.effort == ReasoningEffort.HIGH) 1 else 2
    else -> 0
}

private fun indexToThinkingMode(index: Int): ThinkingMode = when (index) {
    1 -> ThinkingMode.WithEffort(ReasoningEffort.HIGH)
    2 -> ThinkingMode.WithEffort(ReasoningEffort.MAX)
    else -> ThinkingMode.Enabled
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val store = org.koin.compose.koinInject<AppConfigStore>()
    val chatService = org.koin.compose.koinInject<ChatService>()

    var apiKey by remember { mutableStateOf(ConfigService.apiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(ConfigService.model) }
    var maxTokens by remember { mutableStateOf((ConfigService.chatConfig.maxTokens ?: -1).toString()) }
    var temperature by remember { mutableStateOf((ConfigService.chatConfig.temperature ?: 0.7).toString()) }
    var topP by remember { mutableStateOf(ConfigService.chatConfig.topP?.toString() ?: "") }
    var reasoningEffort by remember { mutableStateOf(thinkingModeToIndex(ConfigService.chatConfig.thinkingMode)) }
    var maxToolIterations by remember { mutableStateOf(ConfigService.chatConfig.maxToolIterations.toString()) }
    var luoguCookie by remember { mutableStateOf(ConfigService.luoguCookie) }
    var cookieVisible by remember { mutableStateOf(false) }
    var luoguUid by remember { mutableStateOf(ConfigService.luoguUid) }
    var chatPrompt by remember { mutableStateOf(ConfigService.chatPrompt) }
    var coachPrompt by remember { mutableStateOf(ConfigService.coachPrompt) }
    var showLogs by remember { mutableStateOf(false) }

    fun save() {
        ConfigService.apiKey = apiKey
        ConfigService.model = model
        ConfigService.chatPrompt = chatPrompt
        ConfigService.coachPrompt = coachPrompt
        ConfigService.luoguCookie = luoguCookie
        ConfigService.luoguUid = luoguUid
        ConfigService.chatConfig.maxTokens = maxTokens.toIntOrNull()?.takeIf { it >= 0 }
        ConfigService.chatConfig.temperature = temperature.toDoubleOrNull() ?: 0.7
        ConfigService.chatConfig.topP = topP.toDoubleOrNull()
        ConfigService.chatConfig.thinkingMode = indexToThinkingMode(reasoningEffort)
        ConfigService.chatConfig.maxToolIterations = maxToolIterations.toIntOrNull() ?: 5
        try {
            store.save(ConfigService.toGuiConfig())
        } catch (_: Exception) {
        }
            Logger.info(LogCategory.CONFIG, "settings.save", "api settings saved")
    }

    if (showLogs) {
        LogViewer(onBack = { showLogs = false })
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showLogs = true }) { Text("View Logs") }
                OutlinedButton(onClick = { save() }) { Text("Save") }
                TextButton(onClick = onBack) { Text("← Back") }
            }
        }
        Spacer(Modifier.height(24.dp))

        // ═══ DeepSeek API ═══
        SectionHeader("DeepSeek API")

        SensitiveEditRow(
            label = "API Key", hint = "Your DeepSeek API key",
            value = apiKey, onValueChange = { apiKey = it },
            visible = apiKeyVisible, onToggleVisibility = { apiKeyVisible = !apiKeyVisible },
            placeholder = "sk-...",
        )

        DropdownSettingRow(
            "Model", "DeepSeek model", model,
            if (apiKey.isBlank()) listOf("deepseek-v4-flash", "deepseek-v4-pro") else emptyList(),
            { model = it },
            if (apiKey.isNotBlank()) suspend { chatService.availableModels() } else null,
        )

        EditSettingRow("Max Tokens", "Maximum tokens per response, negative numbers indicate no limit.", maxTokens, { maxTokens = it })
        EditSettingRow("Temperature", "Sampling randomness (0.0–2.0)", temperature, { temperature = it })
        EditSettingRow("Top P", "Nucleus sampling threshold (0.0–1.0)", topP, { topP = it }, "e.g. 0.9")

        val effortHints = mapOf("AUTO" to "Default", "HIGH" to "ReasoningEffort.HIGH", "MAX" to "Max depth")
        DropdownSettingRow(
            "Reasoning Effort", effortHints[EFFORT_OPTIONS[reasoningEffort]] ?: "",
            EFFORT_OPTIONS[reasoningEffort], EFFORT_OPTIONS,
            { reasoningEffort = EFFORT_OPTIONS.indexOf(it) },
        )

        EditSettingRow("Max Tool Iterations", "Max tool-call loops per request", maxToolIterations, { maxToolIterations = it })

        Spacer(Modifier.height(24.dp))

        // ═══ Luogu API ═══
        SectionHeader("Luogu API")
        SensitiveEditRow("Cookie", "Luogu login cookie", luoguCookie, { luoguCookie = it },
            cookieVisible, { cookieVisible = !cookieVisible }, "_uid=...")
        EditSettingRow("UID", "Your Luogu user ID", luoguUid, { luoguUid = it })

        Spacer(Modifier.height(24.dp))

        SectionHeader("Prompts")
        ExpandablePromptRow("Chat Prompt", chatPrompt, { chatPrompt = it }, "System prompt for chat mode")
        ExpandablePromptRow("Coach Prompt", coachPrompt, { coachPrompt = it }, "System prompt for coach mode")

        Spacer(Modifier.height(24.dp))
        SectionHeader("📦 洛谷缓存")
        val cacheManager = org.koin.compose.koinInject<LuoguCacheManager>()
        var cacheStats by remember { mutableStateOf<GlobalCacheStats?>(null) }

        LaunchedEffect(Unit) {
            cacheStats = cacheManager.getGlobalStats()
        }

        if (cacheStats != null) {
            SettingRow("缓存条目", "${cacheStats!!.totalCount}", "")
            SettingRow("缓存大小", formatBytes(cacheStats!!.totalSizeBytes), "")
        } else {
            SettingRow("缓存状态", "加载中...", "")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    cacheManager.clearAll()
                    cacheStats = cacheManager.getGlobalStats()
                }
            }) { Text("清除全部缓存") }
        }
    }
}

// ── Log viewer ──

@Composable
private fun LogViewer(onBack: () -> Unit) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var category by remember { mutableStateOf<LogCategory?>(null) }
    val logs = remember(refreshKey, category) { Logger.recent(500, category = category) }
    var selectedLog by remember { mutableStateOf<LogEntryData?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Logs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { refreshKey++ }) { Text("Refresh") }
                OutlinedButton(onClick = { Logger.clearAll(); refreshKey++ }) { Text("Clear All") }
                OutlinedButton(onClick = { openDirectory(Logger.logLocation) }) { Text("打开日志目录") }
                TextButton(onClick = onBack) { Text("← Back") }
            }
        }
        Text(
            "日志目录: ${Logger.logLocation}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("全部") })
            LogCategory.entries.forEach { cat ->
                FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.name) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No logs yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(logs) { entry ->
                    Surface(
                        onClick = { selectedLog = entry },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                        color = if (entry.category == LogCategory.HTTP) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.level.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = levelColor(entry.level))
                            Spacer(Modifier.width(8.dp))
                            Text(entry.category.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(entry.event, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(entry.message, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(formatLogTime(entry.timestamp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    selectedLog?.let { log ->
        LogDetailDialog(log = log, onDismiss = { selectedLog = null })
    }
}

@Composable
private fun LogDetailDialog(log: LogEntryData, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${log.category.name} · ${log.event}") },
        text = {
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingRow("Time", formatLogTime(log.timestamp), "")
                    SettingRow("Level", log.level.name, "")
                    log.sessionId?.let { SettingRow("Session", it, "") }
                    log.durationMs?.let { SettingRow("Duration", "$it ms", "") }
                    Spacer(Modifier.height(8.dp))
                    Text("Message", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(maskApiKey(log.message), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))

                    when (val detail = Logger.parseDetail(log)) {
                        is LogDetail.Http -> HttpDetailSection(detail.value)
                        is LogDetail.Assistant -> AssistantDetailSection(detail.value)
                        null -> log.detailJson?.let { raw ->
                            Text("Detail", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Text(
                                maskApiKey(raw),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun HttpDetailSection(detail: HttpLogDetail) {
    SettingRow("Method", detail.method, "")
    SettingRow("URL", detail.url, "")
    detail.status?.let { SettingRow("Status", it.toString(), "") }
    detail.sseEventCount.takeIf { it > 0 }?.let { SettingRow("SSE Events", it.toString(), "") }
    Text("Request Headers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    Text(maskApiKey(detail.requestHeaders ?: "(none)"),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    detail.requestBody?.let { body ->
        Text("Request Body", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(maskApiKey(body), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    }
    Text("Response Headers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Text(maskApiKey(detail.responseHeaders ?: "(none)"),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    detail.responseSummary?.let { summary ->
        Text("Response Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(maskApiKey(summary), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AssistantDetailSection(detail: AssistantMessageDetail) {
    SettingRow("Message", detail.messageId, "")
    detail.finishReason?.let { SettingRow("Finish Reason", it, "") }
    detail.totalTokens?.let { SettingRow("Tokens", it.toString(), "") }
    Text("Content", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    Text(detail.content, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    detail.thinking?.takeIf { it.isNotBlank() }?.let { thinking ->
        Text("Thinking", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(thinking, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    }
    detail.segmentsJson?.let { segments ->
        Text("Segments (原始数据)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(segments, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    }
    detail.toolCallsJson?.let { calls ->
        Text("ToolCalls (原始数据)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(calls, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
    }
}

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG -> Color(0xFF9E9E9E)
    LogLevel.INFO -> Color(0xFF4CAF50)
    LogLevel.WARN -> Color(0xFFFF9800)
    LogLevel.ERROR -> Color(0xFFF44336)
}

private fun formatLogTime(epochMillis: Long): String = try {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
} catch (_: Exception) {
    "-"
}

private fun maskApiKey(text: String): String =
    text.replace(Regex("sk-[a-zA-Z0-9]{10,}")) { "<sk-${it.value.takeLast(4)}>" }

// ── Composable helpers ──

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
    HorizontalDivider(Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingRowShell(label: String, hint: String, control: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val gap = if (maxWidth > 640.dp) 48.dp else if (maxWidth > 480.dp) 24.dp else 12.dp
        val inputW = if (maxWidth > 640.dp) 340.dp else if (maxWidth > 480.dp) 280.dp else 200.dp
        val labelW = if (maxWidth > 640.dp) 280.dp else 200.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Column(Modifier.width(labelW)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            control(Modifier.width(inputW))
        }
    }
}

@Composable
private fun EditSettingRow(label: String, hint: String, value: String, onChange: (String) -> Unit, placeholder: String = "") {
    SettingRowShell(label, hint) { mod ->
        OutlinedTextField(value, onChange, mod, placeholder = { Text(placeholder) }, singleLine = true, shape = RoundedCornerShape(12.dp))
    }
}

@Composable
private fun SensitiveEditRow(
    label: String, hint: String, value: String, onValueChange: (String) -> Unit,
    visible: Boolean, onToggleVisibility: () -> Unit, placeholder: String = "",
) {
    SettingRowShell(label, hint) { mod ->
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = mod,
            placeholder = { Text(placeholder) }, singleLine = true, shape = RoundedCornerShape(12.dp),
            visualTransformation = if (!visible) PasswordVisualTransformation('*') else VisualTransformation.None,
            trailingIcon = {
                TextButton(onClick = onToggleVisibility) { Text(if (visible) "🙈" else "👁") }
            },
        )
    }
}

@Composable
private fun DropdownSettingRow(
    label: String, hint: String, currentLabel: String, items: List<String>,
    onSelect: (String) -> Unit, fetchRemoteItems: (suspend () -> List<String>)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var remoteItems by remember { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val displayItems = remoteItems ?: items

    LaunchedEffect(fetchRemoteItems) {
        if (fetchRemoteItems != null && remoteItems == null) {
            loading = true
            try {
                remoteItems = fetchRemoteItems()
            } catch (_: Exception) {
                remoteItems = emptyList()
            }
            loading = false
        }
    }

    SettingRowShell(label, hint) { mod ->
        ExposedDropdownMenuBox(
            expanded = expanded && displayItems.isNotEmpty(),
            onExpandedChange = { if (displayItems.isNotEmpty()) expanded = it }, modifier = mod,
        ) {
            OutlinedTextField(
                value = if (loading) "Loading..." else currentLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                enabled = !loading && displayItems.isNotEmpty(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                displayItems.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onSelect(item); expanded = false }) }
            }
        }
    }
}

@Composable
private fun ExpandablePromptRow(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Surface(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), Arrangement.SpaceBetween) {
                Column {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${value.length} chars " + if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value, onValueChange = onChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 400.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> (bytes / 1_048_576.0).toFixed(1) + " MB"
    bytes >= 1024 -> (bytes / 1024.0).toFixed(1) + " KB"
    else -> "$bytes B"
}

@Composable
private fun SettingRow(label: String, value: String, hint: String) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        val w = maxWidth
        val gap = if (w > 640.dp) 48.dp else if (w > 480.dp) 24.dp else 12.dp
        val labelW = if (w > 640.dp) 280.dp else 200.dp
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), Arrangement.spacedBy(gap)) {
                Column(Modifier.width(labelW)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
