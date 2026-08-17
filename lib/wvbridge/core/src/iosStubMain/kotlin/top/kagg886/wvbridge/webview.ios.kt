package top.kagg886.wvbridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 非 macOS 主机的 iOS 编译桩（见 WebViewConfig.ios.kt 说明）。
 * 运行时不会被调用。
 */
@Composable
public actual fun WebView(
    controller: WebViewController<*>,
    modifier: Modifier,
) {
    error("wvbridge iOS backend is unavailable on this host (requires macOS/Apple SDK)")
}
