package com.github.hatoyuze.luogu.gui.presentation.components.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.github.hatoyuze.luogu.gui.platform.appCacheDir
import com.github.hatoyuze.luogu.gui.platform.ioDispatcher
import com.github.hatoyuze.luogu.gui.platform.sha256Hex
import com.github.hatoyuze.luogu.gui.platform.systemFileSystem
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path

/**
 * [ImageTransformer] that caches remote images under the user-home cache dir
 * (`cachePath/img/<sha256(URL)>.<ext>`).
 *
 * Cached images are served from the local filesystem; uncached images are
 * downloaded via Ktor [HttpClient] and written to the cache directory.
 * `file://` and `data:` URIs pass through directly to Coil.
 */
object CachingImageTransformer : ImageTransformer {

    internal val fileSystem: FileSystem = systemFileSystem
    internal val cacheDir: Path = appCacheDir("img")

    /** Reused HTTP client — avoids creating (and closing) one per download. */
    internal val httpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
        }
    }

    @Composable
    override fun transform(link: String): ImageData? {
        // Passthrough for local / data URIs
        if (link.startsWith("file://") || link.startsWith("data:")) {
            return coil3Load(link)
        }

        val hash = sha256Hex(link)
        val ext = extractExt(link) ?: "png"
        val cachePath = cacheDir / "$hash.$ext"

        // Track whether the file is cached; trigger download if not
        var exists by remember(link) { mutableStateOf(fileSystem.exists(cachePath)) }

        if (!exists) {
            LaunchedEffect(link) {
                try {
                    withContext(ioDispatcher) {
                        val bytes = download(link)
                        fileSystem.createDirectories(cacheDir, mustCreate = false)
                        val sink = fileSystem.sink(cachePath)
                        try {
                            val buffer = Buffer().apply { write(bytes) }
                            sink.write(buffer, buffer.size)
                            sink.flush()
                        } finally {
                            sink.close()
                        }
                        trimImageCache()
                    }
                    exists = true
                } catch (_: Exception) { /* fall back to remote URL below */ }
            }
        }

        return coil3Load(if (exists) cachePath.toString() else link)
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        var size by remember(painter) { mutableStateOf(painter.intrinsicSize) }
        if (painter is AsyncImagePainter) {
            val ps = painter.state.value.painter?.intrinsicSize
            if (ps != null) size = ps
        }
        return size
    }

    @Composable
    private fun coil3Load(data: Any): ImageData {
        val model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(data)
            .size(coil3.size.Size.ORIGINAL)
            .build()
        return ImageData(rememberAsyncImagePainter(model))
    }
}

// ── file-private helpers ──

private suspend fun download(url: String): ByteArray {
    return CachingImageTransformer.httpClient.get(url).body()
}

/** Bounded cache directory: evict oldest files when count or total size is exceeded. */
private fun trimImageCache() {
    try {
        val names = CachingImageTransformer.fileSystem.list(CachingImageTransformer.cacheDir)
            .map { it.name }
        if (names.size <= MAX_CACHE_FILES) return
        names.sorted().take(names.size - MAX_CACHE_FILES).forEach { name ->
            try {
                CachingImageTransformer.fileSystem.delete(
                    CachingImageTransformer.cacheDir / name,
                )
            } catch (_: Exception) {
                // best-effort
            }
        }
    } catch (_: Exception) {
        // best-effort cleanup
    }
}

private const val MAX_CACHE_FILES = 200

private fun extractExt(url: String): String? {
    val path = url.substringBefore('?').substringBefore('#')
    val dot = path.lastIndexOf('.')
    if (dot < 0) return null
    // Only accept a plain alphanumeric extension; anything else (slashes,
    // percent-encoding, …) would corrupt the cache filename/path.
    return path.substring(dot + 1).lowercase().takeIf { it.matches(EXT_PATTERN) }
}

private val EXT_PATTERN = Regex("[a-z0-9]{1,8}")
