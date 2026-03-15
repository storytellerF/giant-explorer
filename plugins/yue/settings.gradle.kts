@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/storytellerF/jksify")
            credentials {
                // 需要配置在~/.gradle/gradle.properties
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
            mavenContent {
                includeGroupAndSubgroups("com.storytellerF.jksify")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/storytellerF/giant-explorer")
            credentials {
                // 需要配置在~/.gradle/gradle.properties
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
            mavenContent {
                includeGroupAndSubgroups("com.storytellerF.giant_explorer")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "yue"
include(":app")
include(":yue-plugin")