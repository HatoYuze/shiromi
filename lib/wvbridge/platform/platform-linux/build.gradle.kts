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
// build so toolchain-less environments (e.g. without webkit2gtk dev headers)
// can still compile the Kotlin side. The produced jar then lacks libwvbridge.so
// and the WebView engine fails at runtime — intended for compile-only checks.
val skipNative = providers.gradleProperty("wvbridge.skipNative").isPresent

val processBuild = tasks.register<Exec>("processBuild") {
    onlyIf {
        System.getProperty("os.name").startsWith("Linux") && !skipNative
    }
    workingDir = project.file("native")
    environment("JAVA_HOME", java17Launcher.get().metadata.installationPath.asFile.absolutePath)
    commandLine(
        "bash", "-c",
        """
            mkdir -p build && \
            cd build && \
            cmake .. && \
            make
        """.trimIndent()
    )
}

// Configure JVM processResources task
tasks.named<ProcessResources>("processResources") {
    dependsOn(processBuild)
    from(project.file("native/build/lib/libwvbridge.so"))
}


publishing(KotlinJvm())
