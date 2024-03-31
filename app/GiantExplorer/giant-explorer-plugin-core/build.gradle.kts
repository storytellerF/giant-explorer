import com.storyteller_f.version_manager.Versions
import com.storyteller_f.version_manager.baseLibrary
import com.storyteller_f.version_manager.commonAppDependency
import com.storyteller_f.version_manager.unitTestDependency

val versionManager: String by project

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.storyteller_f.version_manager")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.storyteller_f.plugin_core"

    defaultConfig {
        minSdk = 21
    }

}
baseLibrary()

dependencies {
    commonAppDependency()
    unitTestDependency()
    //fixme
    constraints {
        listOf(
            "composite-compiler-ksp",
            "ext-func-compiler",
            "ui-list-annotation-compiler-ksp",
        ).forEach {
            ksp("${Versions.JITPACK_RELEASE_GROUP}:$it:$versionManager")
        }
    }
}