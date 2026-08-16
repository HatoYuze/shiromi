import com.vanniktech.maven.publish.KotlinJvm
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.vanniktech.maven.publish")
}

group = "top.kagg886.wvbridge"
version()

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val java17Launcher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}

// Dev affordance (shiromi fork): `-Pwvbridge.skipNative=true` skips the native
// build so toolchain-less environments can still compile the Kotlin side.
val skipNative = providers.gradleProperty("wvbridge.skipNative").isPresent

val processBuild = tasks.register<Exec>("processBuild") {
    onlyIf {
        System.getProperty("os.name").startsWith("Win") && !skipNative
    }
    workingDir = project.file("native")
    environment("JAVA_HOME", java17Launcher.get().metadata.installationPath.asFile.absolutePath)
    commandLine(
        "powershell", "-NoProfile", "-Command",
        // 普通字符串 + 转义 $，避免依赖 kotlin-dsl 实验性 `$$"""` 多美元插值特性
        "cmake -S . -B build;" +
            " if (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }" +
            " cmake --build build --config Debug;" +
            " if (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }"
    )
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(processBuild)
    from(project.file("native/build/wvbridge.dll"))
}

publishing(KotlinJvm())
