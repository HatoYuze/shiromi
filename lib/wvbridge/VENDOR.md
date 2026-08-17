# Vendored wvbridge fork

This directory is a vendored copy of [magic-cucumber/wvbridge](https://github.com/magic-cucumber/wvbridge),
integrated into shiromi as a Gradle composite build (`includeBuild("lib/wvbridge")` in
`settings.gradle.kts`). The `top.kagg886.wvbridge:*` coordinates in
`gradle/libs.versions.toml` are placeholders that the composite build substitutes with
these local projects.

## Upstream

- Repository: https://github.com/magic-cucumber/wvbridge
- Pinned commit: `c5caf67216dd5c51b2adf4276d6e4df36721daf4` (HEAD when vendored)
- License: see `LICENSE` in the upstream repo / this tree.

## Local changes (shiromi fork patch)

1. `settings.gradle.kts` — removed the `:sample:*` includes (samples are not needed and
   would otherwise be configured by the composite build).
2. `platform/{platform-windows,platform-macos,platform-linux}/build.gradle.kts` — added a
   dev flag `-Pwvbridge.skipNative=true` that skips the native engine build, so
   toolchain-less environments (e.g. without `libwebkit2gtk-4.1-dev`) can still compile
   the Kotlin side. The resulting jar then lacks the native library and the WebView
   engine fails at runtime — intended for compile-only checks only. `platform-windows`
   also replaces the experimental `$$"""` string with escaped plain strings (kotlin-dsl
   compatibility under Gradle 8.14).
3. (Cookie API) `core/src/jvmMain/.../cookies.jvm.kt` — NEW public
   `suspend fun WebViewController<*>.getCookieString(url: String): String?` (no EDT hop;
   the JNI call is blocking and must be invoked from a worker dispatcher);
   `core/src/jvmMain/.../internal/WebViewBridgePanel.kt` — added the matching
   `external fun getCookieString(webview: Long, url: String): String?` + wrapper and
   marked `navigationInterceptor` `@Volatile`;
   `platform/*/native/src/jni/get-cookie-string.{cpp,mm,cpp}` — one native function per
   desktop backend reading the engine cookie store (incl. HttpOnly cookies).
4. (Hardening from shiromi review) `core/src/jvmMain/.../controller.jvm.kt` —
   `JvmNavigationInterceptor` handler containers switched to `ConcurrentHashMap`
   (UI thread registers, native thread invokes); `evaluateScript` and the panel now log
   only result lengths, never raw results (may contain session cookies). Same logging
   hardening in `core/src/androidMain` and `core/src/iosMain` controllers.
   `platform-macos/.../get-cookie-string.mm` filters cookies by RFC 6265 domain-match
   (host == domain or `"." + domain` suffix), not substring. `platform-linux/...` checks
   the `gtk_run_on_thread_sync` return value so a stopped GTK runtime cannot leave the
   promise unresolved.
5. (iOS cross-compilation on non-macOS hosts) `core/build.gradle.kts` gates the iOS
   cinterop (`protocol.def`, needs the Apple SDK) to macOS hosts only; on other hosts
   `core/src/iosStubMain/` supplies signature-identical API stubs
   (`WebViewPlatformConfig`, `defaultPlatformConfig`, `rememberWebViewController`,
   `WebView` — all throw at runtime) so the iOS klibs can be cross-compiled by
   Kotlin/Native on Linux/Windows for compile-level verification. Real iOS builds and
   all publishing must run on macOS (publish tasks fail fast on non-macOS hosts).

## How it is consumed

- Build requirement: the composite build runs under shiromi's Gradle. AGP 8.13.2 (used by
  the vendored `core`'s `com.android.kotlin.multiplatform.library` plugin) requires
  Gradle >= 8.13 — shiromi's wrapper is therefore pinned to 8.14.3.
- Desktop JVM: `composeApp` `jvmMain` selects the per-OS platform module
  (`platform-windows` / `platform-macos` / `platform-linux`) at configuration time.
- Android: `commonMain` depends on `core` only; the app reads cookies via
  `android.webkit.CookieManager` directly (no native change needed).
- iOS: native cookie read not yet wired (falls back to `document.cookie` + manual paste).
  Note: on non-macOS hosts the iOS klib is an API stub (`src/iosStubMain`, see local
  change #5 above) — real iOS builds must run on macOS.
- The Kotlin plugin's `kmpPartiallyResolvedDependenciesChecker` emits warnings that the
  android variant of `core` is unresolved — a false positive of the AGP KMP plugin in a
  composite build; `:wvbridge:core:compileAndroidMain` compiles fine and the task
  succeeds.

## Updating

Re-vendor: clone upstream, checkout the target commit, re-apply the patch list above,
and update this file.
