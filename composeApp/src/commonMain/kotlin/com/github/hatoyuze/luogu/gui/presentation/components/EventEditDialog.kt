package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.X
import dev.zt64.compose.pipette.HsvColor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import com.github.hatoyuze.luogu.gui.presentation.utils.toHex2
import com.github.hatoyuze.luogu.gui.presentation.utils.toPad2

// ═══════════════════════════════════════════════════════════════
// EventEditDialog — self-contained event editing dialog
// ═══════════════════════════════════════════════════════════════

private const val MAX_NAME_LENGTH = 64

/** 解析 "HH:mm" 文本 → 分钟数（0..1439）；非法返回 null。 */
internal fun parseTimeMinutes(text: String): Int? {
    val parts = text.split(":").map { it.trim() }
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

@Composable
fun EventEditDialog(
    date: LocalDate,
    initialName: String,
    initialColor: Int,        // ARGB int; 0 = use default
    initialPinned: Boolean = false,
    existingEventId: String?,
    onSave: (name: String, color: Int, pinned: Boolean, allDay: Boolean, timeMinutes: Int?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    showTime: Boolean = false,
    initialAllDay: Boolean = false,
    initialTimeMinutes: Int? = null,
) {
    // ── State ──
    var name by remember { mutableStateOf(initialName) }
    var pinned by remember { mutableStateOf(initialPinned) }
    var allDay by remember { mutableStateOf(initialAllDay) }
    var timeText by remember(initialTimeMinutes) {
        mutableStateOf(
            initialTimeMinutes?.let { "${(it / 60).toPad2()}:${(it % 60).toPad2()}" } ?: ""
        )
    }

    val initialHsv = remember(initialColor) {
        if (initialColor != 0) HsvColor(Color(initialColor))
        else HsvColor(0f, 0.72f, 0.82f)  // warm red default
    }
    var hsvColor by remember { mutableStateOf(initialHsv) }
    var hexInput by remember(initialColor) {
        mutableStateOf(
            if (initialColor != 0) argbToHex(initialColor) else argbToHex(hsvToArgb(initialHsv))
        )
    }

    fun syncHexFromHsv() { hexInput = argbToHex(hsvToArgb(hsvColor)) }
    fun syncHsvFromHex(hex: String) {
        val clean = hex.trimStart('#').take(6)
        if (clean.length == 6) {
            val rgb = clean.toIntOrNull(16) ?: return
            hsvColor = HsvColor(Color(rgb or (0xFF shl 24)))
        }
    }

    Surface(
        modifier = Modifier.width(320.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* consume click — block propagation to scrim */ },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // ── Title ──
            Text(
                "标记事件 · ${date.month.number}月${date.day}日",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            // ── Name input ──
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                placeholder = { Text("事件名称...", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Time controls（移动端 showTime=true 时显示）──
            if (showTime) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("全天", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { input ->
                            timeText = input.filter { it.isDigit() || it == ':' }.take(5)
                        },
                        placeholder = { Text("HH:mm", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        // 非空但格式非法时红色提示，避免静默丢弃
                        isError = timeText.isNotBlank() && parseTimeMinutes(timeText) == null,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Color preview bar ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(hsvColor.toColor()),
            )

            Spacer(Modifier.height(16.dp))

            // ── Hue slider ──
            Text("色相", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            GradientSlider(
                value = hsvColor.hue,
                onValueChange = { hsvColor = hsvColor.copy(hue = it); syncHexFromHsv() },
                valueRange = 0f..359f,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Red, Color.Yellow, Color.Green,
                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                    )
                ),
            )

            Spacer(Modifier.height(12.dp))

            // ── Saturation slider ──
            Text("饱和度", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            GradientSlider(
                value = hsvColor.saturation,
                onValueChange = { hsvColor = hsvColor.copy(saturation = it); syncHexFromHsv() },
                valueRange = 0f..1f,
                brush = Brush.horizontalGradient(
                    listOf(Color.White, Color.hsv(hsvColor.hue, 1f, hsvColor.value))
                ),
            )

            Spacer(Modifier.height(14.dp))

            // ── Hex input ──
            OutlinedTextField(
                value = hexInput,
                onValueChange = { input ->
                    val filtered = input.filter { it in "0123456789ABCDEFabcdef" }.take(6)
                    hexInput = filtered
                    syncHsvFromHex(filtered)
                },
                placeholder = { Text("RRGGBB", fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                leadingIcon = {
                    Text(
                        "#",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            // ── Action buttons (icon-only, right-aligned) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: pin toggle
                IconButton(onClick = { pinned = !pinned }) {
                    Icon(
                        PinIcon,
                        contentDescription = if (pinned) "取消置顶" else "置顶",
                        modifier = Modifier.size(16.dp),
                        tint = if (pinned) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }

                // Right: delete / cancel / save
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (existingEventId != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                FeatherIcons.Trash2,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            FeatherIcons.X,
                            contentDescription = "取消",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }

                    IconButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                val timeMinutes = if (allDay) null else parseTimeMinutes(timeText)
                                onSave(name.trim(), hsvToArgb(hsvColor), pinned, allDay, timeMinutes)
                            }
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Icon(
                            FeatherIcons.Check,
                            contentDescription = "保存",
                            tint = if (name.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// GradientSlider — custom slider with gradient track
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(
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

// ═══════════════════════════════════════════════════════════════
// Color conversion helpers
// ═══════════════════════════════════════════════════════════════

private fun hsvToArgb(hsv: HsvColor): Int {
    val c = hsv.toColor()
    val r = (c.red * 255).toInt()
    val g = (c.green * 255).toInt()
    val b = (c.blue * 255).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun argbToHex(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return r.toHex2() + g.toHex2() + b.toHex2()
}

// ═══════════════════════════════════════════════════════════════
// Pin icon — GitHub Octicon "pin" (SVG → ImageVector)
// ═══════════════════════════════════════════════════════════════

internal val PinIcon: ImageVector
    get() {
        if (_pinIcon != null) return _pinIcon!!
        _pinIcon = ImageVector.Builder(
            name = "Pin",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveToRelative(11.294f, 0.984f)
                lineToRelative(3.722f, 3.722f)
                arcToRelative(1.75f, 1.75f, 0f, false, true, -0.504f, 2.826f)
                lineToRelative(-1.327f, 0.613f)
                arcToRelative(3.089f, 3.089f, 0f, false, false, -1.707f, 2.084f)
                lineToRelative(-0.584f, 2.454f)
                curveToRelative(-0.317f, 1.332f, -1.972f, 1.8f, -2.94f, 0.832f)
                lineTo(5.75f, 11.311f)
                lineTo(1.78f, 15.28f)
                arcToRelative(0.749f, 0.749f, 0f, true, true, -1.06f, -1.06f)
                lineToRelative(3.969f, -3.97f)
                lineToRelative(-2.204f, -2.204f)
                curveToRelative(-0.968f, -0.968f, -0.5f, -2.623f, 0.832f, -2.94f)
                lineToRelative(2.454f, -0.584f)
                arcToRelative(3.08f, 3.08f, 0f, false, false, 2.084f, -1.707f)
                lineToRelative(0.613f, -1.327f)
                arcToRelative(1.75f, 1.75f, 0f, false, true, 2.826f, -0.504f)
                close()
            }
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(6.283f, 9.723f)
                lineToRelative(2.732f, 2.731f)
                arcToRelative(0.25f, 0.25f, 0f, false, false, 0.42f, -0.119f)
                lineToRelative(0.584f, -2.454f)
                arcToRelative(4.586f, 4.586f, 0f, false, true, 2.537f, -3.098f)
                lineToRelative(1.328f, -0.613f)
                arcToRelative(0.25f, 0.25f, 0f, false, false, 0.072f, -0.404f)
                lineToRelative(-3.722f, -3.722f)
                arcToRelative(0.25f, 0.25f, 0f, false, false, -0.404f, 0.072f)
                lineToRelative(-0.613f, 1.328f)
                arcToRelative(4.584f, 4.584f, 0f, false, true, -3.098f, 2.537f)
                lineToRelative(-2.454f, 0.584f)
                arcToRelative(0.25f, 0.25f, 0f, false, false, -0.119f, 0.42f)
                lineToRelative(2.731f, 2.732f)
                close()
            }
        }.build()
        return _pinIcon!!
    }

private var _pinIcon: ImageVector? = null
