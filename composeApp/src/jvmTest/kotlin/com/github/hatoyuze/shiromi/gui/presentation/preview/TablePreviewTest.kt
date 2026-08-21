// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.MathAwareParagraph
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.ShiromiTable
import com.github.hatoyuze.shiromi.gui.presentation.markdown.FoldableFlavourDescriptor
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import java.io.File
import java.util.Base64
import kotlin.test.Test

/**
 * 真实渲染的表格预览捕获：把 markdown 表格（含 `::cute-table{tuack}` 三线表、
 * `^` 跨行合并、LaTeX 公式）经真实 Markdown 管线渲染成 PNG，并生成自包含的
 * `table-preview.html`（base64 内嵌，浏览器双击即看，明暗双主题）。
 *
 * 与 GuiPreviewCaptureTest 一致：设置 `-Pshiromi.screenshot.dir=<dir>` 才写盘，
 * 否则仅做渲染冒烟（CI 安全）。不改 build.gradle.kts。
 *
 * 隔离说明（仅串行）：本类依赖 JUnit4 单 fork 顺序执行（与 GuiPreviewCaptureTest
 * 相同约束，未设置 maxParallelForks）；writeHtml 按已知名称白名单过滤并对整个
 * HTML 全量重写，顺序无关且幂等，不会与并行测试产生写竞争。
 */
@OptIn(ExperimentalTestApi::class)
class TablePreviewTest {

    private fun capture(name: String, width: Int, height: Int, markdown: String, dark: Boolean = false) {
        ScreenshotHarness.capture(name, width, height, PreviewFrame.NONE, darkTheme = dark) {
            TableMarkdown(markdown)
        }
        writeHtml()
    }

    @Test
    fun cuteTable_preview() {
        skipOnCi()
        capture("table-cute", 900, 460, CUTE_MARKDOWN)
        capture("table-cute-dark", 900, 460, CUTE_MARKDOWN, dark = true)
    }

    @Test
    fun cuteTableWide_preview() {
        skipOnCi()
        capture("table-cute-wide", 520, 380, CUTE_WIDE_MARKDOWN)
    }

    @Test
    fun defaultTable_preview() {
        skipOnCi()
        capture("table-default", 640, 360, DEFAULT_MARKDOWN)
    }

    @Test
    fun defaultTableMath_preview() {
        skipOnCi()
        capture("table-default-math", 640, 360, DEFAULT_MATH_MARKDOWN)
    }

    /** 远程 CI 上整个预览用例标记为跳过（JUnit assumption），本地照常执行。 */
    private fun skipOnCi() {
        org.junit.Assume.assumeFalse(
            "table preview capture is disabled on CI (no PNG/HTML artifacts)",
            ScreenshotHarness.isCiEnvironment(),
        )
    }

    // ═══════════════════════════════════════════════════════
    // Sample markdown
    // ═══════════════════════════════════════════════════════

    private val CUTE_MARKDOWN = """
        ::cute-table{tuack}
        | 课程 | 人数 | 平均分 |
        | --- | --- | --- |
        | 高等数学 | 120 | 85.6 |
        | 线性代数 | 98 | ^ |
        | 概率论 | 110 | 79.3 |
        | 毕达哥拉斯公式 | ${'$'}a^2 + b^2 = c^2${'$'} | 备注 |
    """.trimIndent()

    private val CUTE_WIDE_MARKDOWN = """
        ::cute-table{tuack}
        | 课程名称 | 主讲教师 | 学分 | 学时 | 考核方式 | 平均分 | 及格率 | 备注 |
        | --- | --- | --- | --- | --- | --- | --- | --- |
        | 高等数学（一） | 王教授 | 5.0 | 96 | 闭卷考试 | 85.6 | 92% | 全校公共必修课 |
        | 线性代数 | 李教授 | 3.0 | 48 | 闭卷考试 | ^ | ^ | 理工科必修 |
        | 概率论与数理统计 | 张教授 | 4.0 | 64 | 开卷考试 | 79.3 | 88% | 大三第一学期开设 |
    """.trimIndent()

    private val DEFAULT_MARKDOWN = """
        | 城市 | 温度 | 天气 |
        | --- | --- | --- |
        | 北京 | 25°C | 晴 |
        | 上海 | 28°C | 多云 |
        | 广州 | 31°C | ^ |
    """.trimIndent()

    private val DEFAULT_MATH_MARKDOWN = """
        | 变量 | 含义 |
        | --- | --- |
        | ${'$'}\mu${'$'} | 总体均值 |
        | ${'$'}\sigma${'$'} | 总体标准差 |
    """.trimIndent()

    // ═══════════════════════════════════════════════════════
    // Preview composable
    // ═══════════════════════════════════════════════════════

    @Composable
    private fun TableMarkdown(content: String) {
        val colorScheme = MaterialTheme.colorScheme
        val typography = com.github.hatoyuze.shiromi.gui.presentation.rememberMarkdownTypography(colorScheme = colorScheme)
        val colors = markdownColor(
            text = colorScheme.onSurface,
            codeText = colorScheme.primary,
            codeBackground = colorScheme.surfaceVariant.copy(alpha = 0.3f),
            inlineCodeText = colorScheme.primary,
            inlineCodeBackground = colorScheme.surfaceVariant.copy(alpha = 0.2f),
            linkText = colorScheme.primary,
        )
        // 主题背景：真实气泡由 Surface 提供底色，预览需显式铺一层主题 surface，
        // 否则暗色主题下浅色文字落在白画布上无法辨认。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Markdown(
                content = content,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                colors = colors,
                flavour = FoldableFlavourDescriptor(),
                components = markdownComponents(
                    codeBlock = highlightedCodeBlock,
                    codeFence = highlightedCodeFence,
                    table = { ShiromiTable(it) },
                    paragraph = { MathAwareParagraph(it) },
                ),
                typography = typography,
                dimens = markdownDimens(dividerThickness = 1.dp),
                padding = markdownPadding(block = 8.dp),
            )
        }
    }
    // ═══════════════════════════════════════════════════════
    // HTML gallery
    // ═══════════════════════════════════════════════════════

    private fun writeHtml() {
        if (ScreenshotHarness.isCiEnvironment()) return
        val dir = System.getProperty("shiromi.screenshot.dir")?.takeIf { it.isNotBlank() } ?: return
        val rawDir = File(dir, "raw")
        if (!rawDir.isDirectory) return
        // Read path mirrors the write-side name whitelist (letters/digits/-/_ + .png),
        // restricts to this run's known captions (stale artifacts from earlier runs are
        // ignored), rejects symlinks and verifies the PNG magic bytes so a planted file
        // named table-*.png cannot smuggle arbitrary bytes into the gallery.
        val safeName = Regex("[A-Za-z0-9_-]+\\.png")
        val knownCaptions = setOf("table-cute", "table-cute-dark", "table-cute-wide", "table-default", "table-default-math")
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val images = rawDir.listFiles { f ->
            f.isFile &&
                f.name.removeSuffix(".png") in knownCaptions &&
                safeName.matches(f.name) &&
                !java.nio.file.Files.isSymbolicLink(f.toPath()) &&
                f.readBytes().take(8).toByteArray().contentEquals(pngMagic)
        }?.sortedBy { it.name }
            ?: return
        if (images.isEmpty()) return

        val sections = images.joinToString("\n") { image ->
            val caption = image.name.removeSuffix(".png")
            val source = sourceFor(image.name)
            val data = Base64.getEncoder().encodeToString(image.readBytes())
            """
            <section class="case">
              <h2>${escapeHtml(caption)}</h2>
              <pre><code>${escapeHtml(source)}</code></pre>
              <img src="data:image/png;base64,$data" alt="${escapeHtml(caption)}">
            </section>
            """.trimIndent()
        }
        val html = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <title>Shiromi 表格渲染预览</title>
            <style>
              body { font-family: system-ui, "Noto Sans CJK SC", "Microsoft YaHei", sans-serif; margin: 24px; background: #fafafa; color: #222; }
              h1 { font-size: 20px; }
              .case { background: #fff; border: 1px solid #ddd; border-radius: 8px; padding: 16px; margin-bottom: 20px; max-width: 960px; }
              .case h2 { font-size: 15px; margin: 0 0 8px; color: #444; }
              .case pre { background: #f5f5f5; border-radius: 6px; padding: 10px; overflow-x: auto; font-size: 13px; }
              .case img { display: block; margin-top: 12px; max-width: 100%; border: 1px solid #eee; }
              .note { font-size: 13px; color: #666; max-width: 960px; }
            </style>
            </head>
            <body>
            <h1>Shiromi Markdown 表格渲染预览</h1>
            <p class="note">说明：截图由真实 Markdown 渲染管线（无头 Compose）生成。含 <code>$\ldots$</code> 的单元格在本机无 RaTeX 原生库时按设计回退为公式源码文本；Windows 分发打包了原生库，将正常渲染为数学公式。</p>
            $sections
            </body>
            </html>
        """.trimIndent()
        val out = File(dir, "table-preview.html")
        out.writeText(html)
        println("table preview html -> ${out.absolutePath}")
    }

    private fun sourceFor(name: String): String = when {
        name.startsWith("table-cute-wide") -> CUTE_WIDE_MARKDOWN
        name.startsWith("table-cute") -> CUTE_MARKDOWN
        name.startsWith("table-default-math") -> DEFAULT_MATH_MARKDOWN
        name.startsWith("table-default") -> DEFAULT_MARKDOWN
        else -> ""
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
