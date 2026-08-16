import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val prop = { key: String -> (project.findProperty(key) as String?) ?: "" }

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

            // RaTeX native for Windows
            runtimeOnly(libs.ratex.native.windows)

            // wvbridge desktop backend (per-OS native engine)
            implementation(
                when {
                    System.getProperty("os.name").startsWith("Windows") -> libs.wvbridge.platform.windows
                    System.getProperty("os.name").startsWith("Linux") -> libs.wvbridge.platform.linux
                    System.getProperty("os.name").startsWith("Mac") && System.getProperty("os.arch") in setOf("aarch64", "arm64") -> libs.wvbridge.platform.macos
                    else -> error(
                        "Unsupported desktop runtime for wvbridge: " +
                            "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
                    )
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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        mainClass = "com.github.hatoyuze.luogu.gui.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Msi,
            )
            packageName = "shiromi"
            packageVersion = "0.1.0"

            windows {
                menuGroup = "shiromi"
            }
        }
    }
}

sqldelight {
    databases {
        create("LuoguDatabase") {
            packageName.set("com.github.hatoyuze.luogu.gui")
        }
    }
}
