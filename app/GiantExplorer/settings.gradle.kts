@file:Suppress("UnstableApiUsage")

val filterFolder: String? by settings
val baoFolder: String? by settings
val liFolder: String? by settings
pluginManagement {
//    includeBuild("../../../common-ui-list/version-manager")
//    includeBuild("../../../common-ui-list/common-publish")
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { setUrl("https://jitpack.io") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://artifactory.cronapp.io/public-release/") }
    }
}
rootProject.name = "GiantExplorer"
include(":giant-explorer")
include(":giant-explorer-plugin-core")

val commonUiPath = File(rootDir, "../../../common-ui-list")

listOf<String>(
//    "ui-list",
//    "common-vm-ktx",
//    "ui-list-annotation-definition",
//    "ext-func-definition",
//    "ext-func-compiler",
//    "slim-ktx"
).forEach {
    val modulePath = File(commonUiPath, it)
    if (modulePath.exists()) {
        include(it)
        project(":$it").projectDir = modulePath
    }
}

val home: String = System.getProperty("user.home")
when (filterFolder) {
    "local" -> file("$home/AndroidStudioProjects/FilterUIProject/")
    "submodule" -> file("../../FilterUIProject")
    else -> null
}?.let {
    if (it.exists()) {
        val l = listOf(
            "config-core",
            "filter-core",
            "sort-core",
            "config_edit",
            "filter-ui",
            "sort-ui",
            "recycleview_ui_extra"
        )
        l.forEach {
            include("filter:$it")
            project(":filter:$it").projectDir = File(it, it)
        }
    }
}


when (baoFolder) {
    "local" -> file("$home/AndroidStudioProjects/Bao/")
    else -> null
}?.let {
    if (it.exists()) {
        val l = listOf("startup", "bao-library")
        for (sub in l) {
            include("bao:$sub")
            project(":bao:$sub").projectDir = File(it, sub)
        }

    }
}


when (liFolder) {
    "local" -> file("../../giant-explorer/li/plugin")
    else -> null
}?.let {
    if (it.exists()) {
        include("li-plugin")
        project(":li-plugin").projectDir = it
    }
}
