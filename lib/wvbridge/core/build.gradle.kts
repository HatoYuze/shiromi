import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")

    id("org.jetbrains.dokka")
}

group = "top.kagg886.wvbridge"
version()

// iOS cinterop（WKWebView KVO 观察者协议）需要 Apple SDK，只有 macOS 主机能处理。
// 其他主机（如 Linux CI）跳过 cinterop 与 iosMain 实现源码，产出「API 级」iOS klib，
// 供 composeApp 的 iOS 平台编译（klib 交叉编译）解析通过；真实 iOS 构建由 macOS 任务负责。
val isMacHost = System.getProperty("os.name").lowercase().contains("mac")

library(
    ios = {
        if (isMacHost) {
            compilations.all {
                cinterops {
                    val protocol by creating {
                        defFile("src/iosMain/interop/protocol.def")
                        packageName("top.kagg886.wvbridge.internal")
                        includeDirs("src/iosMain/interop/include")
                    }
                }
            }
        }
    }
) {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.ui)
            implementation(libs.foundation)
        }

        androidMain.dependencies {
            implementation(libs.androidx.webkit)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compileTaskProvider.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }
}


// 非 macOS 主机：iosMain 实现依赖 cinterop 生成的协议类型，无法编译，
// 改用 iOS API 桩源码集（src/iosStubMain，签名一致、运行时不可达）。
if (!isMacHost) {
    kotlin.sourceSets.getByName("iosMain").kotlin.setSrcDirs(listOf("src/iosStubMain/kotlin"))

    // 非 macOS 主机产出的 iOS klib 为 API 桩（无真实 WebKit 后端），禁止发布；
    // 发布必须来自 macOS 主机（真实 iosMain + cinterop）。
    tasks.configureEach {
        if (name.startsWith("publish")) {
            doFirst {
                check(false) {
                    "wvbridge:core must be published from a macOS host: " +
                        "non-macOS hosts produce iOS API stubs (src/iosStubMain) without the real WebKit backend."
                }
            }
        }
    }
}

publishing(KotlinMultiplatform())
