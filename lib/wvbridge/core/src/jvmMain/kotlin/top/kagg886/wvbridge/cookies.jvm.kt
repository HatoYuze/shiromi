package top.kagg886.wvbridge

import kotlinx.coroutines.suspendCancellableCoroutine
import top.kagg886.wvbridge.internal.WebViewBridgePanel
import kotlin.coroutines.resume

/**
 * Reads the cookie header string (`name=value; name=value`, HttpOnly included)
 * for [url] from the native desktop WebView session.
 *
 * Returns `null` when the controller is not a JVM desktop controller (e.g. the
 * Android/iOS controller on a non-desktop target) or when the native engine is
 * not yet attached.
 *
 * Shiromi fork addition (see `lib/wvbridge/VENDOR.md`). Cookie values must not
 * be logged by callers.
 *
 * Thread model: the underlying JNI call is blocking, but each desktop backend
 * marshals internally to its own engine thread (WebView2 / GTK main loop / WKWebView
 * main queue), so invoking it from a worker thread is safe and avoids blocking the
 * EDT. Callers should run this on a background dispatcher (the app wraps it with
 * `Dispatchers.IO` + `withTimeout`).
 */
public suspend fun WebViewController<*>.getCookieString(url: String): String? {
    val panel = instance as? WebViewBridgePanel ?: return null
    return suspendCancellableCoroutine { continuation ->
        try {
            continuation.resume(panel.getCookieString(url))
        } catch (t: Throwable) {
            continuation.resumeWith(Result.failure(t))
        }
    }
}
