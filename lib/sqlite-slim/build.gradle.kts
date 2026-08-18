// sqlite-jdbc 平台裁剪包：为桌面分发目标生成只含目标平台原生库的 sqlite-jdbc jar。
// 仅当构建带 -Pdist=windows|linux|macos 时，composeApp 通过 dependencySubstitution
// 用本项目替换 org.xerial:sqlite-jdbc；开发运行（run，无 -Pdist）仍使用官方全量 jar。
plugins {
    `java-library`
}

val distOs: String = (project.findProperty("dist") as String?)?.lowercase() ?: "auto"

// 与 composeApp 保持同一套分发目标逻辑：auto 按主机探测，未知值直接报错
//（避免在 Linux 上无 -Pdist 时静默产出 Windows 裁剪包）。
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

val sqliteJdbc by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // 只取 sqlite-jdbc 本体（其 slf4j-api 传递依赖不需要，也不应进入产物）
    isTransitive = false
}

dependencies {
    sqliteJdbc(libs.sqlite.jdbc)
}

// 每个分发目标只保留桌面常见的原生目录（glibc 平台；Linux-Musl/FreeBSD 不打包）：
// 桌面 JVM 以 x86_64 为主，另保留 aarch64 覆盖 Apple Silicon / Windows ARM 等新硬件。
val nativeIncludePatterns: List<String> = when (distTarget) {
    "windows" -> listOf(
        "org/sqlite/native/Windows/x86_64/**",
        "org/sqlite/native/Windows/aarch64/**",
    )
    "linux" -> listOf(
        "org/sqlite/native/Linux/x86_64/**",
        "org/sqlite/native/Linux/aarch64/**",
    )
    "macos" -> listOf(
        "org/sqlite/native/Mac/x86_64/**",
        "org/sqlite/native/Mac/aarch64/**",
    )
    else -> error("Unsupported distribution target: $distTarget")
}

// 惰性解析 sqlite-jdbc 的 zipTree（执行期求值，不触发配置期依赖解析）
val sqliteJdbcTree: () -> FileTree = { zipTree(sqliteJdbc.singleFile) }

tasks.jar {
    inputs.files(sqliteJdbc)
    from(sqliteJdbcTree) {
        exclude("org/sqlite/native/**")
    }
    from(sqliteJdbcTree) {
        include(nativeIncludePatterns)
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
