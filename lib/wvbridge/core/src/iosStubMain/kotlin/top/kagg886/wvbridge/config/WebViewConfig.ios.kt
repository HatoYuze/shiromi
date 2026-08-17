package top.kagg886.wvbridge.config

/**
 * 非 macOS 主机（Linux CI 等）的 iOS 编译桩。
 *
 * 真实 iOS 实现（src/iosMain）依赖 Apple SDK 的 cinterop，只能在 macOS 上构建；
 * 此桩仅用于让 wvbridge core 的 iOS klib 在无 Apple SDK 的主机上可交叉编译，
 * 供 composeApp 的 iOS 平台编译做 API 级解析。真实 iOS 构建由 macOS 任务负责。
 */
public actual class WebViewPlatformConfig

/** 桩：返回空配置（运行时不会被调用）。 */
public actual fun defaultPlatformConfig(): WebViewPlatformConfig = WebViewPlatformConfig()
