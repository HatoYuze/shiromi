# SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

# shiromi — R8 混淆/裁剪规则（Android release）
# 目标：在开启 minify 后保住序列化、DI、原生库加载等反射路径。

# ---------- kotlinx.serialization ----------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
# @Serializable 数据类（含 deepseek-helper / luogu-protocol 中的模型）
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    <init>(...);
}
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# ---------- Koin DI ----------
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.KoinRef *;
}
-dontwarn org.koin.**

# ---------- JNA（RaTeX 数学渲染依赖） ----------
-keep class com.sun.jna.** { *; }
-keepclassmembers class * {
    @com.sun.jna.Structure *;
}
-dontwarn com.sun.jna.**

# ---------- JNI 原生方法（kompressor/zstd、sqlite-jdbc） ----------
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------- SQLDelight / sqlite-jdbc ----------
-keep class app.cash.sqldelight.** { *; }
-keep class org.sqlite.** { *; }
-dontwarn app.cash.sqldelight.**

# ---------- Ktor ----------
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ---------- OkHttp / Okio ----------
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------- Coil ----------
# Coil 3 的 HTTP fetcher（coil-network-ktor3）经 ServiceLoader 注册，
# 不带 keep 规则会被 R8 剥成空壳，导致 release 包远程图片加载失败。
-keep class coil3.network.ktor3.** { *; }
-dontwarn coil.**
-dontwarn androidx.core.graphics.**

# ---------- wvbridge（内嵌 WebView 登录） ----------
-dontwarn top.kagg886.**

# ---------- Compose（官方 consumer 规则之外的兜底） ----------
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.compose.**

# ---------- kotlinx.coroutines ----------
-dontwarn kotlinx.coroutines.**

# ---------- 其他可选依赖告警 ----------
-dontwarn com.google.errorprone.**
-dontwarn org.slf4j.**
