rootProject.name = "shiromi"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":lib:luogu-protocol")

// Vendored wvbridge fork (lib/wvbridge) — adds native cookie access for the
// embedded Luogu login WebView. Composite build substitutes the
// top.kagg886.wvbridge:* coordinates declared in gradle/libs.versions.toml.
includeBuild("lib/wvbridge")
