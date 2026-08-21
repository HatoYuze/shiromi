// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设备外框设计 token 与绘制组件：在真实页面截图外层套上桌面窗口/手机机身，
 * 桌面 = macOS 红黄绿圆点标题栏 + 圆角 + 暖色阴影；手机 = 深色机身 + 状态栏 +
 * 居中挖孔摄像头 + 底部手势条。
 *
 * 所有尺寸均为设计常量（dp），内容区尺寸由调用方（ScreenshotHarness）约束。
 */
internal object DeviceFrames {
    // ── 桌面窗口 ──
    const val DesktopMargin = 28
    const val DesktopTitleBarHeight = 40
    val PageBackground = Color(0xFFE8E5DC)
    val WindowSurface = Color(0xFFFDFBF7)
    val WindowBorder = Color(0xFFD6D0BF)
    val TitleDivider = Color(0xFFE6E0D2)
    val TitleText = Color(0xFF7D7968)
    val DotRed = Color(0xFFFF5F57)
    val DotYellow = Color(0xFFFEBC2E)
    val DotGreen = Color(0xFF28C840)

    // ── 手机机身 ──
    const val MobileMargin = 28
    const val MobileBezel = 10
    const val MobileStatusBarHeight = 44
    val PhoneBody = Color(0xFF1C1C1E)
    val PhoneHighlight = Color(0xFF3A3A3E)
    val CameraHole = Color(0xFF101014)
    val StatusBarBackground = Color(0xFFF5F1EB)
    val StatusIcon = Color(0xFF4A4540)
    val BatteryFill = Color(0xFF4A8C6F)
    val HomeIndicator = Color(0x8A3A3A3C)
}

/** 桌面窗口外框：暖色页面背景 + 圆角窗口 + macOS 风格标题栏。 */
@Composable
internal fun DesktopWindowFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(DeviceFrames.PageBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .shadow(elevation = 26.dp, shape = RoundedCornerShape(16.dp), clip = false)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, DeviceFrames.WindowBorder, RoundedCornerShape(16.dp))
                .background(DeviceFrames.WindowSurface),
        ) {
            // ── 标题栏：红黄绿圆点 + 应用名 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DeviceFrames.DesktopTitleBarHeight.dp)
                    .background(DeviceFrames.WindowSurface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(16.dp))
                TrafficDot(DeviceFrames.DotRed)
                Spacer(Modifier.width(8.dp))
                TrafficDot(DeviceFrames.DotYellow)
                Spacer(Modifier.width(8.dp))
                TrafficDot(DeviceFrames.DotGreen)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "shiromi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DeviceFrames.TitleText,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DeviceFrames.TitleDivider),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun TrafficDot(color: Color) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
}

/** 手机外框：深色机身 + 圆角屏幕（状态栏 + 内容）+ 挖孔摄像头 + 底部手势条。 */
@Composable
internal fun PhoneFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(DeviceFrames.PageBackground),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .shadow(elevation = 30.dp, shape = RoundedCornerShape(48.dp), clip = false)
                .clip(RoundedCornerShape(48.dp))
                .background(DeviceFrames.PhoneBody)
                .border(1.dp, DeviceFrames.PhoneHighlight, RoundedCornerShape(48.dp))
                .padding(DeviceFrames.MobileBezel.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White),
            ) {
                Column {
                    StatusBar()
                    Box(Modifier.fillMaxWidth()) {
                        content()
                        // 底部手势条（覆盖在应用内容之上，与真机一致）
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .size(width = 120.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(DeviceFrames.HomeIndicator),
                        )
                    }
                }
                // 居中挖孔摄像头
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(DeviceFrames.CameraHole),
                )
            }
        }
    }
}

/** 安卓风格状态栏：左侧时间，右侧信号/Wi-Fi/电池。 */
@Composable
private fun StatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceFrames.MobileStatusBarHeight.dp)
            .background(DeviceFrames.StatusBarBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "09:41",
            modifier = Modifier.padding(start = 24.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeviceFrames.StatusIcon,
        )
        Spacer(Modifier.weight(1f))
        StatusIcons(Modifier.padding(end = 20.dp))
    }
}

/** 信号四格 + Wi-Fi + 电池，纯 Canvas 矢量绘制，不依赖图标字体。 */
@Composable
private fun StatusIcons(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 64.dp, height = 14.dp)) {
        val c = DeviceFrames.StatusIcon
        val dp = 1.dp.toPx()

        // ── 信号：4 根渐高圆角条 ──
        val barWidth = 3f * dp
        val barGap = 2f * dp
        val barBottom = 13f * dp
        val heights = listOf(4f, 7f, 10f, 13f)
        heights.forEachIndexed { i, h ->
            drawRoundRect(
                color = c,
                topLeft = Offset(i * (barWidth + barGap), barBottom - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(1f * dp, 1f * dp),
            )
        }

        // ── Wi-Fi：三层向下的弧 + 圆点 ──
        val wifiCenterX = 22f * dp
        val wifiCenterY = 13f * dp
        val arcRadii = listOf(6f * dp, 4f * dp, 2f * dp)
        arcRadii.forEach { r ->
            drawArc(
                color = c,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(wifiCenterX - r, wifiCenterY - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 1.5f * dp),
            )
        }
        drawCircle(color = c, radius = 1.1f * dp, center = Offset(wifiCenterX, wifiCenterY))

        // ── 电池：轮廓 + 绿色电量 + 电极 ──
        val bx = 44f * dp
        val by = 3f * dp
        val bw = 18f * dp
        val bh = 8f * dp
        drawRoundRect(
            color = c,
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(2f * dp, 2f * dp),
            style = Stroke(width = 1.2f * dp),
        )
        drawRoundRect(
            color = DeviceFrames.BatteryFill,
            topLeft = Offset(bx + 2f * dp, by + 2f * dp),
            size = Size(bw * 0.58f, bh - 4f * dp),
            cornerRadius = CornerRadius(1f * dp, 1f * dp),
        )
        drawRoundRect(
            color = c,
            topLeft = Offset(bx + bw + 0.5f * dp, by + 2f * dp),
            size = Size(1.6f * dp, bh - 4f * dp),
            cornerRadius = CornerRadius(0.8f * dp, 0.8f * dp),
        )
    }
}
