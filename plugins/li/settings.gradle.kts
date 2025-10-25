pluginManagement {
    // includeBuild("../../common-ui-list/version-manager")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https.jitpack.io")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https.jitpack.io")
        }
    }
}
rootProject.name = "li"
include(":app")
include(":plugin")
include(":giant-explorer-plugin-core")
project(":giant-explorer-plugin-core").projectDir = File("../../app/GiantExplorer/giant-explorer-plugin-core")
