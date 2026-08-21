// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.shiromi.gui.theme.LuoguTheme
import java.io.File
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** 预览输出的外框形态。 */
enum class PreviewFrame {
    /** 无外框：纯页面内容。 */
    NONE,

    /** 桌面窗口外框（macOS 风格圆点标题栏 + 圆角 + 暖色阴影）。 */
    DESKTOP,

    /** 手机外框（状态栏 + 挖孔摄像头 + 手势条）。 */
    MOBILE,
}

/**
 * 无头 GUI 预览捕获器：复用 [com.github.hatoyuze.shiromi.gui.presentation.home.HomeLayoutRenderTest]
 * 的 captureToImage 管线，把真实页面内容按 [PreviewFrame] 套上桌面/手机外框后导出 PNG。
 *
 * 写盘开关沿用 Gradle 透传：`-Pshiromi.screenshot.dir=<dir>`（未设置时测试只做渲染与
 * 断言，CI 零写盘）。framed 输出到 `<dir>/framed/{desktop,mobile}/`，同一渲染的无外框
 * 裸图输出到 `<dir>/raw/`（供校验真实页面像素）。
 *
 * 线程模型：runDesktopComposeUiTest 在测试线程驱动 Compose 场景；本类只做渲染与
 * 编码，不持有跨测试状态。
 */
@OptIn(ExperimentalTestApi::class)
object ScreenshotHarness {

    private val screenshotDir: String? =
        System.getProperty("shiromi.screenshot.dir")?.takeIf { it.isNotBlank() }

    /** True on remote CI (GitHub Actions / GitLab set `CI`): preview PNGs/HTML must never be written there. */
    internal fun isCiEnvironment(): Boolean = !System.getenv("CI").isNullOrEmpty()

    /**
     * 捕获一页。内容 composable 会被约束在 [contentWidth]x[contentHeight] 内（frame
     * 为 DESKTOP/MOBILE 时在画布上居中套壳）。
     *
     * @param setup 渲染就绪后在截图前执行的动作（如驱动引导页翻页、等待 ViewModel 状态）。
     *   注意：framed 与 raw 两次顶层渲染都会执行 setup（共享 ViewModel 的路径须幂等）。
     */
    fun capture(
        name: String,
        contentWidth: Int,
        contentHeight: Int,
        frame: PreviewFrame = PreviewFrame.NONE,
        darkTheme: Boolean = false,
        setup: ComposeUiTest.() -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        requireNameSafe(name)
        val dir = screenshotDir
        val (canvasW, canvasH) = canvasSize(contentWidth, contentHeight, frame)
        runDesktopComposeUiTest(width = canvasW, height = canvasH) {
            setContent {
                ThemeWrap(darkTheme) {
                    DeviceFrameWrap(frame, contentWidth, contentHeight, content)
                }
            }
            waitForIdle()
            setup()
            waitForIdle()
            if (dir != null && !isCiEnvironment()) {
                writePng(File(dir, framedRelativePath(name, frame)), capturePng())
            }
        }
        // 裸图：与 framed 同内容、同尺寸，但作为**顶层**第二次渲染（不嵌套在第一个
        // Compose 场景内，避免嵌套 runTest 依赖框架未守卫的角落行为）。
        if (dir != null && frame != PreviewFrame.NONE && !isCiEnvironment()) {
            runDesktopComposeUiTest(width = contentWidth, height = contentHeight) {
                setContent { ThemeWrap(darkTheme) { content() } }
                waitForIdle()
                setup()
                waitForIdle()
                writePng(File(dir, "raw/$name.png"), capturePng())
            }
        }
    }

    /** 截图文件名白名单：仅允许字母数字与 -/_，防止路径逃逸。 */
    private fun requireNameSafe(name: String) {
        require(name.isNotBlank() && name.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "unsafe screenshot name: '$name' (only letters/digits/-/_ allowed)"
        }
    }

    private fun framedRelativePath(name: String, frame: PreviewFrame): String = when (frame) {
        PreviewFrame.NONE -> "raw/$name.png"
        PreviewFrame.DESKTOP -> "framed/desktop/$name.png"
        PreviewFrame.MOBILE -> "framed/mobile/$name.png"
    }

    /** 按外框计算画布尺寸（内容 + 外框 + 阴影边距）。 */
    private fun canvasSize(w: Int, h: Int, frame: PreviewFrame): Pair<Int, Int> = when (frame) {
        PreviewFrame.NONE -> w to h
        PreviewFrame.DESKTOP ->
            (w + 2 * DeviceFrames.DesktopMargin) to
                (h + DeviceFrames.DesktopTitleBarHeight + 1 + 2 * DeviceFrames.DesktopMargin)
        PreviewFrame.MOBILE ->
            (w + 2 * (DeviceFrames.MobileBezel + DeviceFrames.MobileMargin)) to
                (h + DeviceFrames.MobileStatusBarHeight + 2 * (DeviceFrames.MobileBezel + DeviceFrames.MobileMargin))
    }

    @Composable
    private fun ThemeWrap(darkTheme: Boolean, content: @Composable () -> Unit) {
        if (darkTheme) LuoguTheme(darkTheme = true) { content() } else LuoguTheme { content() }
    }

    @Composable
    private fun DeviceFrameWrap(
        frame: PreviewFrame,
        w: Int,
        h: Int,
        content: @Composable () -> Unit,
    ) {
        when (frame) {
            PreviewFrame.NONE -> content()
            PreviewFrame.DESKTOP -> DesktopWindowFrame {
                Box(Modifier.size(w.dp, h.dp)) { content() }
            }
            PreviewFrame.MOBILE -> PhoneFrame {
                Box(Modifier.size(w.dp, h.dp)) { content() }
            }
        }
    }

    /**
     * 捕获全部根（主窗口 + Dialog/ModalBottomSheet 弹出层）并按坐标拼到同一画布。
     * 单根时与 onRoot() 等价；多根时弹出层内容叠加在主窗口之上，保证事件编辑弹窗/
     * 底弹层等页面也能完整出图。
     */
    private fun ComposeUiTest.capturePng(): ByteArray {
        val roots = onAllNodes(isRoot()).fetchSemanticsNodes()
        val images = roots.indices.map { index ->
            onAllNodes(isRoot())[index].captureToImage()
        }
        if (images.size == 1) return encodePng(images[0])
        val width = roots.maxOf { it.boundsInRoot.right.toInt() }
        val height = roots.maxOf { it.boundsInRoot.bottom.toInt() }
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32Premul(width, height))
        val canvas = Canvas(bitmap)
        roots.zip(images).forEach { (node, img) ->
            canvas.drawImage(
                Image.makeFromBitmap(img.asSkiaBitmap()),
                node.boundsInRoot.left,
                node.boundsInRoot.top,
                null,
            )
        }
        return Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private fun encodePng(image: androidx.compose.ui.graphics.ImageBitmap): ByteArray =
        Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes

    private fun writePng(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        assertTrue(file.length() > 0, "screenshot written: ${file.path}")
    }
}
