// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val prop = { key: String -> (project.findProperty(key) as String?) ?: "" }

// CI 发版版本：优先 APP_VERSION_NAME 环境变量（由 .github/workflows/publish-release.yml 注入 tag 版本），
// 其次 -PappVersion=<ver>；缺省 0.1.0。versionCode 同理（APP_VERSION_CODE / -PappVersionCode，缺省 1）。
// 注意：回退属性不能叫 version——那是 Gradle 内置项目属性（未设置时恒为 "unspecified"），
// 会导致 Compose 桌面打包的 deb 版本校验失败（Illegal version for 'Deb'）。
val releaseVersion: String = (System.getenv("APP_VERSION_NAME") ?: prop("appVersion")).removePrefix("v").ifBlank { "0.1.0" }
val releaseVersionCode: Int = (System.getenv("APP_VERSION_CODE") ?: prop("appVersionCode")).toIntOrNull() ?: 1

// 本机 Android release 签名配置：读取 ~/.android/keystore.properties（不入仓库，仓库保持可移植）。
// 文件缺失时 release 产物保持 unsigned 并给出 apksigner 手动签名提示；文件损坏时降级为 unsigned 并告警，
// 不因本机签名配置问题拖垮桌面/iOS 等其他构建任务。
val androidKeystoreDir: File = file(System.getProperty("user.home") + "/.android")
val androidKeystoreProps: Properties? = run {
    val f = androidKeystoreDir.resolve("keystore.properties")
    if (f.isFile) {
        val props = Properties().apply { f.inputStream().use { load(it) } }
        val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").filter { props.getProperty(it) == null }
        if (missing.isNotEmpty()) {
            logger.warn("⚠️ ~/.android/keystore.properties 缺少字段 ${missing.joinToString()}，release APK 将保持未签名。")
            null
        } else {
            props
        }
    } else {
        null
    }
}

// 参与 sqlite-jdbc 替换的 JVM 配置（打包类路径 + 测试类路径）
val JVM_PACKAGING_CONFIGURATIONS = setOf(
    "jvmRuntimeClasspath",
    "jvmCompileClasspath",
    "jvmTestRuntimeClasspath",
    "jvmTestCompileClasspath",
)

// 桌面分发目标：-Pdist=windows|linux|macos（缺省 auto = 按构建主机自动探测）。
// 每个分发目标只装配自己平台的原生依赖（wvbridge 引擎、RaTeX native、sqlite-jdbc 瘦身包）。
// 注意：-Pdist 是打包专用参数；run/test 应使用与主机匹配的目标（否则原生库缺失会静默运行失败）。
val distOs: String = (project.findProperty("dist") as String?)?.lowercase() ?: "auto"
val hostOs: String = when {
    System.getProperty("os.name").startsWith("Windows") -> "windows"
    System.getProperty("os.name").startsWith("Linux") -> "linux"
    System.getProperty("os.name").startsWith("Mac") -> "macos"
    else -> error("Unsupported desktop runtime: ${System.getProperty("os.name")}")
}
val distTarget: String = when (distOs) {
    "auto" -> hostOs
    "windows", "linux", "macos" -> distOs
    else -> error("Unknown distribution target: $distOs (expected windows|linux|macos)")
}

// run / test 等开发任务与打包目标不一致时直接报错，而不是产出运行时崩溃
//（例如 Linux 上 -Pdist=windows 会得到空 wvbridge jar + 无 Linux sqlite 原生库）。
tasks.configureEach {
    if (name == "run" || name == "test" || name.startsWith("jvmTest") || name.startsWith("allTests")) {
        doFirst {
            check(distTarget == hostOs) {
                "任务 $name 需要主机平台 '$hostOs'，但 -Pdist=$distOs 指定了 '$distTarget'。" +
                    "-Pdist 仅用于分发打包（createDistributable/createExe/createMsi 等），开发运行请去掉该参数。"
            }
        }
    }
}

// 测试 JVM 透传 -Pshiromi.screenshot.dir=<dir>：HomeLayoutRenderTest 用它导出渲染 PNG。
tasks.withType<Test>().configureEach {
    systemProperty("shiromi.screenshot.dir", (project.findProperty("shiromi.screenshot.dir") as String?) ?: "")
}

// coil 3.0.4 与 3.2.0 版本冲突：multiplatform-markdown-renderer-coil3 传递依赖旧的
// coil/coil-compose 3.0.4，与应用侧 coil 3.2.0 并存于类路径。统一强制到 3.2.0。
configurations.configureEach {
    resolutionStrategy {
        force(
            "io.coil-kt.coil3:coil:3.2.0",
            "io.coil-kt.coil3:coil-compose:3.2.0",
        )
    }
}

// 分发打包时用平台裁剪后的 sqlite-jdbc（:lib:sqlite-slim）替换全平台 jar，
// 安装包不再携带 Linux/macOS/Android 的 sqlite 原生库。开发运行（run）不受影响。
// 仅作用于 JVM 相关配置，避免未来 sqlite-jdbc 进入 Android 配置时被错误替换。
if (distOs != "auto") {
    configurations.matching { it.name in JVM_PACKAGING_CONFIGURATIONS }.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xerial:sqlite-jdbc")).using(project(":lib:sqlite-slim"))
        }
    }
}

// sqlite-jdbc 版本统一由项目自身决定（与 sqldelight sqlite-driver 2.0.2 的
// 传递要求一致），避免 catalog 声明值与实际解析值分叉。
configurations.configureEach {
    resolutionStrategy.force("org.xerial:sqlite-jdbc:${libs.versions.sqliteJdbc.get()}")
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.android.application)
}

kotlin {
    jvm()
    androidTarget()
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts += "-lsqlite3"
        }
    }

    // Re-apply the default hierarchy template after declaring targets so the
    // template-created source sets (iosMain, iosArm64Main, iosSimulatorArm64Main, …)
    // stay wired to their compilations even though we add a custom intermediate below.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // Pure-Java platform glue shared by desktop JVM and Android
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmAndroidMain)
        jvmMain.get().dependsOn(jvmAndroidMain)

        commonMain.dependencies {
            // Project dependencies
            implementation(project(":lib:luogu-protocol"))
            implementation(libs.deepseek.helper)

            // Compose Multiplatform
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)

            // Lifecycle ViewModel for Compose
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.runtime.compose)

            // Koin DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.composables.core) // RightSideSheet dialog

            // Ktor HTTP client (core only; engines per platform)
            implementation(libs.ktor.client.core)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Color picker
            implementation(libs.pipette)

            // KMP file I/O
            implementation(libs.kotlinx.io.core)
            implementation(libs.okio)

            // Markdown rendering
            implementation(libs.markdown.renderer.m3)
            implementation(libs.markdown.renderer.code)
            implementation(libs.markdown.renderer.coil3)

            // Icons
            implementation(libs.feather.icons)

            // Coil image loading
            implementation(libs.coil.compose.core)
            implementation(libs.coil.network.ktor3)
            implementation(libs.ratex)

            // SQLDelight coroutines extension
            implementation(libs.sqldelight.coroutines.extensions)

            // zstd compression (Kompressor)
            api(project.dependencies.platform("com.ensody.kompressor:kompressor-bom:${libs.versions.kompressor.get()}"))
            implementation(libs.kompressor.core)
            implementation(libs.kompressor.zstd.nativelib)

            // Embedded browser (wvbridge fork) for the in-app Luogu login
            implementation(libs.wvbridge.core)
        }

        jvmMain.dependencies {
            // Ktor CIO engine
            implementation(libs.ktor.client.cio)

            // Compose Desktop
            implementation(compose.desktop.currentOs)

            // SQLDelight SQLite driver
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.sqlite.jdbc)

            // zstd native library for JVM
            implementation(libs.zstd.libzstd)

            // RaTeX native — 仅 Windows 分发需要（其余平台不打包）
            if (distTarget == "windows") {
                runtimeOnly(libs.ratex.native.windows)
            }

            // wvbridge desktop backend (per-OS native engine)
            implementation(
                when (distTarget) {
                    "windows" -> libs.wvbridge.platform.windows
                    "linux" -> libs.wvbridge.platform.linux
                    "macos" -> libs.wvbridge.platform.macos
                    else -> error("Unsupported desktop runtime for wvbridge: $distTarget")
                },
            )
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

android {
    namespace = "com.github.hatoyuze.shiromi"
    compileSdk = prop("COMPILE_SDK").toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        applicationId = "com.github.hatoyuze.shiromi"
        minSdk = prop("MIN_SDK").toInt()
        targetSdk = prop("TARGET_SDK").toInt()
        versionCode = releaseVersionCode
        versionName = releaseVersion
    }

    if (androidKeystoreProps != null) {
        signingConfigs.create("release") {
            // 相对路径按 ~/.android/ 解析（properties 文件所在目录），绝对路径原样使用
            storeFile = file(androidKeystoreProps.getProperty("storeFile")).let {
                if (it.isAbsolute) it else androidKeystoreDir.resolve(it.path)
            }
            storePassword = androidKeystoreProps.getProperty("storePassword")
            keyAlias = androidKeystoreProps.getProperty("keyAlias")
            keyPassword = androidKeystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (androidKeystoreProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // 按 CPU 架构拆包：每个分发 APK 只保留一个 ABI 的原生库
    // （zstd/ratex 原生库是 APK 体积大户），另产出 universal 全量包作兜底。
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    dependencies {
        // kompressor (zstd) Android 构件要求 core library desugaring
        coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.github.hatoyuze.shiromi.gui.MainKt"

        nativeDistributions {
            // 每个分发目标对应不同的安装包格式（Exe/Msi / Deb/AppImage / Dmg）
            when (distTarget) {
                "windows" -> targetFormats(
                    TargetFormat.Exe,
                    TargetFormat.Msi,
                )
                "linux" -> targetFormats(
                    TargetFormat.Deb,
                    TargetFormat.AppImage,
                )
                "macos" -> targetFormats(TargetFormat.Dmg)
                else -> error("Unsupported distribution target: $distTarget")
            }
            packageName = "shiromi"
            packageVersion = releaseVersion

            if (distTarget == "windows") {
                windows {
                    menuGroup = "shiromi"
                }
            }
        }
    }
}

sqldelight {
    databases {
        create("LuoguDatabase") {
            packageName.set("com.github.hatoyuze.shiromi.gui")
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 一键发版 releaseAll — 按当前主机的构建能力自动组合各平台产物
// ═══════════════════════════════════════════════════════════
// 规则：
//   * 桌面端（当前主机格式：Linux=Deb / macOS=Dmg / Windows=Exe+Msi）— 总是构建
//     （AppImage 在 Compose 1.11 已弃用，packageAppImage 为空操作，无产物）
//   * Android APK（assembleRelease）— 检测到 Android SDK 时构建（ANDROID_HOME / ANDROID_SDK_ROOT / local.properties sdk.dir）
//   * iOS（Release frameworks + 模拟器 .app）— 仅 macOS + Xcode
// 产物目录：
//   desktop  composeApp/build/compose/binaries/main/<format>/
//   android  composeApp/build/outputs/apk/release/
//   ios      composeApp/build/bin/iosArm64|iosSimulatorArm64/releaseFramework/
//            composeApp/build/ios-simulator/Build/Products/Release-iphonesimulator/
// 用法：
//   ./gradlew :composeApp:releaseAll    # 一键构建主机支持的全部产物
//   ./gradlew :composeApp:releasePlan   # 只打印主机能力与将要构建的产物，不构建
//   ./gradlew :composeApp:releaseDesktop / releaseAndroid / releaseIos   # 单平台

// Android SDK 探测交给 AGP 自己回答（SdkLocator：优先 local.properties 的 sdk.dir，其次
// ANDROID_HOME / ANDROID_SDK_ROOT；且用 java.util.Properties 解析，Windows 上
// `C\:\\Users\\...` 转义能正确还原）。探测失败/缺失时返回 null → 跳过 Android。
val androidSdkDir: String? = runCatching {
    extensions.getByType<com.android.build.gradle.BaseExtension>().sdkDirectory?.absolutePath
}.getOrNull()
val hasAndroidToolchain = !androidSdkDir.isNullOrBlank()

// macOS 上探测活动 Xcode 工具链：默认路径之外（Xcode-beta / xcodes 管理）也识别。
fun activeXcodeToolchain(): Boolean = try {
    val p = ProcessBuilder("xcode-select", "-p").start()
    val out = p.inputStream.bufferedReader().readText().trim()
    p.waitFor()
    p.destroy()
    out.isNotBlank() && !out.contains("CommandLineTools")
} catch (_: Exception) { false }
val hasXcode = hostOs == "macos" && (File("/Applications/Xcode.app").exists() || activeXcodeToolchain())

val releasePlan by tasks.registering {
    group = "release"
    description = "打印当前主机的构建能力与 releaseAll 将要构建的产物（不执行构建）"
    doLast {
        println("════════════ 主机能力检测 ════════════")
        println("主机系统: $hostOs（桌面分发目标: $distTarget）")
        println("Android SDK: ${if (hasAndroidToolchain) "已检测到（由 AGP 解析）" else "未检测到（跳过 Android APK）"}")
        println("iOS 工具链: ${if (hasXcode) "已检测到（Xcode）" else "未检测到（跳过 iOS；仅 macOS + Xcode 可构建）"}")
        println()
        println("将要构建:")
        println("  [总是] desktop  → ${desktopFormatLabels(distTarget)}")
        if (hasAndroidToolchain) println("  [有SDK] android  → release APK（arm64-v8a / x86_64 / universal，按本机签名配置）")
        if (hasXcode) println("  [有Xcode] ios    → Release frameworks（device+simulator）+ 模拟器 .app（免签名）")
        println("═══════════════════════════════════════")
    }
}

val releaseDesktop by tasks.registering {
    group = "release"
    description = "构建当前主机的桌面分发安装包（Linux=Deb（AppImage 已弃用/空操作）/ macOS=Dmg / Windows=Exe+Msi）"
    dependsOn("packageDistributionForCurrentOS")
    doFirst {
        check(distOs == "auto" || distTarget == hostOs) {
            "releaseDesktop/releaseAll 只做本机分发（当前主机=$hostOs，-Pdist=$distOs 目标=$distTarget）。" +
                "跨平台打包请用 ./gradlew :composeApp:createDistributable -Pdist=$distTarget。"
        }
    }
    doLast {
        println("桌面产物: composeApp/build/compose/binaries/main/${distTarget}/")
    }
}

val releaseAndroid: TaskProvider<Task> = if (hasAndroidToolchain) {
    tasks.register("releaseAndroid") {
        group = "release"
        description = "构建 Android release APK（按 ABI 拆包；检测到 ~/.android/keystore.properties 时自动签名）"
        dependsOn("assembleRelease")
        doLast {
            val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            val apks = apkDir.listFiles { f -> f.name.endsWith(".apk") }.orEmpty().sortedBy { it.name }
            println("Android 产物: ${apkDir.absolutePath}")
            apks.forEach { println("  - ${it.name}") }
            if (androidKeystoreProps != null) {
                println("✅ 已使用 ~/.android/keystore.properties 自动签名（alias=${androidKeystoreProps.getProperty("keyAlias")}）")
            } else {
                println("⚠️ 未配置签名（缺少 ~/.android/keystore.properties），APK 为未签名；" +
                    "请创建该文件（storeFile/storePassword/keyAlias/keyPassword）后重新构建，或先用 apksigner 手动签名。")
            }
        }
    }
} else {
    tasks.register("releaseAndroid") {
        group = "release"
        description = "未检测到 Android SDK（ANDROID_HOME / ANDROID_SDK_ROOT / local.properties sdk.dir），跳过"
        doLast { println("⏭ 跳过 Android：未检测到 Android SDK") }
    }
}

// iOS 分两步：releaseIosFramework 只做 Gradle 侧 framework 链接（确定性强，等价于 CI ios-build），
// iosBuildSimulatorApp 通过 xcodebuild 产出模拟器 .app（内部会再触发一次 gradlew embed，属预期）。
val releaseIosFramework: TaskProvider<Task> = if (hasXcode) {
    tasks.register("releaseIosFramework") {
        group = "release"
        description = "链接 iOS Release frameworks（device + simulator）"
        dependsOn("linkReleaseFrameworkIosArm64", "linkReleaseFrameworkIosSimulatorArm64")
        doLast {
            println("iOS framework 产物:")
            println("  - composeApp/build/bin/iosArm64/releaseFramework/")
            println("  - composeApp/build/bin/iosSimulatorArm64/releaseFramework/")
        }
    }
} else {
    tasks.register("releaseIosFramework") {
        group = "release"
        description = "未检测到 Xcode（仅 macOS 可链接 iOS framework），跳过"
        doLast { println("⏭ 跳过 iOS framework：未检测到 Xcode") }
    }
}

val iosBuildSimulatorApp: TaskProvider<Exec>? = if (hasXcode) {
    tasks.register<Exec>("iosBuildSimulatorApp") {
        group = "release"
        description = "xcodebuild 构建 iOS 模拟器 .app（CODE_SIGNING_ALLOWED=NO，免签名）"
        workingDir = rootProject.projectDir
        commandLine(
            "xcodebuild",
            "-project", "iosApp/shiromi.xcodeproj",
            "-scheme", "shiromi",
            "-configuration", "Release",
            "-sdk", "iphonesimulator",
            "-derivedDataPath", "composeApp/build/ios-simulator",
            "CODE_SIGNING_ALLOWED=NO",
            "build",
        )
        doLast {
            println("iOS 模拟器 .app: composeApp/build/ios-simulator/Build/Products/Release-iphonesimulator/shiromi.app")
        }
    }
} else {
    null
}

val releaseIos: TaskProvider<Task> = if (hasXcode) {
    tasks.register("releaseIos") {
        group = "release"
        description = "构建 iOS 客户端（Release frameworks + 模拟器 .app）"
        dependsOn("releaseIosFramework")
        iosBuildSimulatorApp?.let { dependsOn(it) }
        doLast {
            println("⚠️ 真机 .ipa 需要签名（Developer 证书 + provisioning profile），请用 Xcode Archive 导出。")
        }
    }
} else {
    tasks.register("releaseIos") {
        group = "release"
        description = "未检测到 Xcode（仅 macOS 可构建 iOS），跳过"
        doLast { println("⏭ 跳过 iOS：未检测到 Xcode") }
    }
}

val releaseAll by tasks.registering {
    group = "release"
    description = "一键构建当前主机支持的全部平台产物（desktop + 按能力 android/ios）"
    dependsOn("releasePlan", "releaseDesktop", "releaseAndroid", "releaseIos")
    doLast {
        println()
        println("════════════ 发版完成 ════════════")
        println("desktop → composeApp/build/compose/binaries/main/$distTarget/")
        if (hasAndroidToolchain) println("android → composeApp/build/outputs/apk/release/（按本机签名配置）")
        if (hasXcode) {
            println("ios     → composeApp/build/bin/*/releaseFramework/")
            println("          composeApp/build/ios-simulator/Build/Products/Release-iphonesimulator/")
        }
        println("══════════════════════════════════")
    }
}

/** 桌面分发格式的展示名（与 -Pdist 目标对应）。 */
fun desktopFormatLabels(target: String): String = when (target) {
    "windows" -> "Exe + Msi"
    // Compose Gradle 插件自 1.8 起弃用 AppImage：1.11 中 packageAppImage 为空操作（无产物），
    // Linux 实际安装包为 Deb。
    "linux" -> "Deb（AppImage 在 Compose 1.11 已弃用，任务为空操作）"
    "macos" -> "Dmg"
    else -> "?"
}
