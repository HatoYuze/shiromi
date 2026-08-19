// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zt64.compose.pipette.HsvColor
import com.github.hatoyuze.luogu.gui.presentation.utils.toHex2
import com.github.hatoyuze.luogu.gui.presentation.utils.toPad2

// ═══════════════════════════════════════════════════════════
// EventEditForm — 移动端底部表单（EventEditSheet）与桌面弹窗（EventEditDialog）
// 共用的状态与颜色选择控件（设计稿 A / A2）。
// ═══════════════════════════════════════════════════════════

/** 事件颜色预设（主题衍生 + 默认暖红），与设计稿一致；自定义色可随时切换。 */
internal val EventColorPresets: List<Int> = listOf(
    // 默认暖红：与 EventEditState 在 initialColor=0 时的初始色同源，保证新建事件色板高亮正确
    hsvToArgb(HsvColor(0f, 0.72f, 0.82f)),
    0xFF4A5599.toInt(), // primary 藏蓝
    0xFFB8752E.toInt(), // secondary 铜金
    0xFF4A8C6F.toInt(), // tertiary 青绿
    0xFF3E8E9E.toInt(), // 青蓝
    0xFF7A6CB5.toInt(), // 紫
    0xFFD9A13B.toInt(), // 琥珀
    0xFF8A8278.toInt(), // 木灰
)

/** 事件编辑表单状态（名称/置顶/全天/时间/颜色），两端表单共用。 */
internal class EventEditState(
    initialName: String,
    initialColor: Int,
    initialPinned: Boolean,
    initialAllDay: Boolean,
    initialTimeMinutes: Int?,
) {
    var name by mutableStateOf(initialName)
    var pinned by mutableStateOf(initialPinned)
    var allDay by mutableStateOf(initialAllDay)
    var timeText by mutableStateOf(
        initialTimeMinutes?.let { "${(it / 60).toPad2()}:${(it % 60).toPad2()}" } ?: ""
    )
    /** 是否展开「自定义颜色」折叠区（预设之外的精调）。 */
    var customExpanded by mutableStateOf(false)

    private val initialHsv: HsvColor =
        if (initialColor != 0) HsvColor(Color(initialColor)) else HsvColor(0f, 0.72f, 0.82f)

    var hsvColor by mutableStateOf(initialHsv)
    var hexInput by mutableStateOf(
        if (initialColor != 0) argbToHex(initialColor) else argbToHex(hsvToArgb(initialHsv))
    )

    /** 时间文本非空但格式非法（红色提示 + 禁止保存）。 */
    val timeInvalid: Boolean
        get() = !allDay && timeText.isNotBlank() && parseTimeMinutes(timeText) == null

    val canSave: Boolean
        get() = name.isNotBlank() && !timeInvalid

    fun syncHexFromHsv() {
        hexInput = argbToHex(hsvToArgb(hsvColor))
    }

    fun syncHsvFromHex(hex: String) {
        val clean = hex.trimStart('#').take(6)
        if (clean.length == 6) {
            val rgb = clean.toIntOrNull(16) ?: return
            hsvColor = HsvColor(Color(rgb or (0xFF shl 24)))
        }
    }

    /** 选中预设色（含自定义折叠状态时返回 null）。 */
    fun selectedPreset(): Int? =
        if (customExpanded) null else EventColorPresets.firstOrNull { it == hsvToArgb(hsvColor) }

    /** 保存时的时间分钟数：全天 → null；否则非法/空 → null。 */
    fun timeMinutesOrNull(): Int? = if (allDay) null else parseTimeMinutes(timeText)
}

/** 预设色板 + 自定义折叠区（色相/饱和度滑杆 + HEX 输入）。 */
@Composable
internal fun EventColorPicker(
    state: EventEditState,
) {
    // 每帧只计算一次当前选中预设（避免每个 swatch 重复做 HSV→ARGB 转换）
    val selectedPreset = state.selectedPreset()

    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EventColorPresets.forEach { argb ->
                ColorSwatch(
                    color = Color(argb),
                    selected = selectedPreset == argb,
                    onClick = {
                        state.customExpanded = false
                        state.hsvColor = HsvColor(Color(argb))
                        state.syncHexFromHsv()
                    },
                )
            }
            // 自定义色：未展开显示「＋」，展开后显示当前颜色并保持选中态
            ColorSwatch(
                color = if (state.customExpanded) state.hsvColor.toColor() else Color.Unspecified,
                selected = state.customExpanded,
                customPlus = true,
                onClick = { state.customExpanded = !state.customExpanded },
            )
        }

        if (state.customExpanded) {
            Spacer(Modifier.height(12.dp))
            Text("色相", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            GradientSlider(
                value = state.hsvColor.hue,
                onValueChange = { state.hsvColor = state.hsvColor.copy(hue = it); state.syncHexFromHsv() },
                valueRange = 0f..359f,
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        Color.Red, Color.Yellow, Color.Green,
                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                    )
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text("饱和度", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            GradientSlider(
                value = state.hsvColor.saturation,
                onValueChange = { state.hsvColor = state.hsvColor.copy(saturation = it); state.syncHexFromHsv() },
                valueRange = 0f..1f,
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color.White, Color.hsv(state.hsvColor.hue, 1f, state.hsvColor.value))
                ),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.hexInput,
                onValueChange = { input ->
                    val filtered = input.filter { it in "0123456789ABCDEFabcdef" }.take(6)
                    state.hexInput = filtered
                    state.syncHsvFromHex(filtered)
                },
                placeholder = { Text("RRGGBB", fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                leadingIcon = {
                    Text("#", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    customPlus: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(
                if (customPlus && !selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                } else {
                    Modifier
                }
            )
            .background(
                if (color == Color.Unspecified) MaterialTheme.colorScheme.surface
                else color,
                CircleShape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (customPlus && !selected) {
            Text("+", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// 供移动端 sheet / 桌面弹窗共用的颜色转换（原 EventEditDialog 私有，现提升为 internal）

internal fun hsvToArgb(hsv: HsvColor): Int {
    val c = hsv.toColor()
    val r = (c.red * 255).toInt()
    val g = (c.green * 255).toInt()
    val b = (c.blue * 255).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

internal fun argbToHex(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return r.toHex2() + g.toHex2() + b.toHex2()
}

// ═══════════════════════════════════════════════════════════
// GradientSlider — custom slider with gradient track（原 EventEditDialog 私有，现共享）
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().height(36.dp),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
                )
            },
            track = { sliderPositions ->
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                ) {
                    drawLine(
                        brush = brush,
                        start = Offset(0f, size.center.y),
                        end = Offset(size.width, size.center.y),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round,
                    )
                }
            },
        )
    }
}
