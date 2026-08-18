package com.github.hatoyuze.luogu.gui.presentation.components.askuser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.platform.currentTimeMillis
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.Clock
import compose.icons.feathericons.HelpCircle
import kotlinx.coroutines.delay
import com.github.hatoyuze.luogu.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.luogu.gui.presentation.utils.toPad2

/**
 * Interactive question card shown above the chat input when the AI agent
 * calls the [askuser] tool.
 *
 * Supports single/multi-select options, optional custom text input,
 * and a countdown timer that triggers timeout when expired.
 */
@Composable
fun AskUserCard(
    desc: String,
    options: List<String>,
    isMulti: Boolean,
    allowCustom: Boolean,
    timeoutMs: Int,
    startedAtMs: Long,
    onAnswer: (selectedOptions: List<String>, customText: String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    var remainingMs by remember { mutableStateOf(timeoutMs - (currentTimeMillis() - startedAtMs)) }
    val timerActive = remainingMs > 0

    // ── Countdown timer ──
    LaunchedEffect(startedAtMs) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = (timeoutMs - (currentTimeMillis() - startedAtMs)).coerceAtLeast(0)
        }
    }

    val timerColor by animateColorAsState(
        targetValue = when {
            remainingMs <= 0 -> colorScheme.error
            remainingMs < 10_000 -> colorScheme.error.copy(alpha = 0.8f)
            else -> colorScheme.onSurfaceVariant
        },
        label = "timerColor",
    )

    val timerText = when {
        remainingMs <= 0 -> "超时"
        remainingMs >= 60_000 -> "${remainingMs / 60_000}:${((remainingMs % 60_000) / 1000).toInt().toPad2()}"
        else -> "${remainingMs / 1000}s"
    }

    // ── Selection state ──
    val selectedOptions = remember { mutableStateListOf<String>() }
    var customText by remember { mutableStateOf("") }
    val canSubmit = selectedOptions.isNotEmpty() || (allowCustom && customText.isNotBlank())

    // ── 收回 / 展开（每条新提问默认展开）──
    var expanded by remember(startedAtMs) { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "askUserChevron",
    )

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.primary.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                // ── Header: icon + question + timer + 收展箭头（可点击）──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        FeatherIcons.HelpCircle,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    // Timer pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = timerColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, timerColor.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                FeatherIcons.Clock,
                                contentDescription = null,
                                tint = timerColor,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = timerText,
                                style = MaterialTheme.typography.labelSmall,
                                color = timerColor,
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        FeatherIcons.ChevronDown,
                        contentDescription = if (expanded) "收回" else "展开",
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp).rotate(chevronRotation),
                    )
                }

                // ── 可收回的选项区 / 自定义输入 / 提交 ──
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        if (options.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))

                    // ── Options list (vertical, scrollable) ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        options.forEach { option ->
                            val isSelected = option in selectedOptions
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) colorScheme.primary.copy(alpha = 0.15f)
                                        else colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) colorScheme.primary.copy(alpha = 0.5f)
                                    else colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        if (isMulti) {
                                            if (isSelected) selectedOptions.remove(option)
                                            else selectedOptions.add(option)
                                        } else {
                                            selectedOptions.clear()
                                            selectedOptions.add(option)
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (compact) {
                                        // 移动端：单选圆点 / 多选方框
                                        val indicatorShape = if (isMulti) RoundedCornerShape(4.dp) else CircleShape
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(indicatorShape)
                                                .background(
                                                    if (isSelected) colorScheme.primary else Color.Transparent,
                                                    indicatorShape,
                                                )
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isSelected) colorScheme.primary
                                                    else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                    shape = indicatorShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (isSelected) {
                                                if (isMulti) {
                                                    Icon(
                                                        AppIcons.SuccessIcon,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = colorScheme.onPrimary,
                                                    )
                                                } else {
                                                    Box(Modifier.size(5.dp).background(colorScheme.onPrimary, CircleShape))
                                                }
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = if (isSelected) colorScheme.primary
                                                else colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Custom text input (only when allowCustom = true) ──
                if (allowCustom) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        placeholder = {
                            Text(
                                "输入自定义回答...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        },
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── Submit button ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    if (compact) {
                        Button(
                            onClick = {
                                if (canSubmit) {
                                    onAnswer(selectedOptions.toList(), customText)
                                }
                            },
                            enabled = canSubmit,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("提交")
                        }
                    } else {
                        Surface(
                            modifier = Modifier.clickable(enabled = canSubmit) {
                                if (canSubmit) {
                                    onAnswer(selectedOptions.toList(), customText)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (canSubmit) colorScheme.primary.copy(alpha = 0.1f)
                                    else colorScheme.primary.copy(alpha = 0.03f),
                            border = BorderStroke(1.dp,
                                if (canSubmit) colorScheme.primary.copy(alpha = 0.2f)
                                else colorScheme.primary.copy(alpha = 0.08f)),
                        ) {
                            Text(
                                "提交",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (canSubmit) colorScheme.primary
                                        else colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
                }
                }
            }
        }
    }
}
