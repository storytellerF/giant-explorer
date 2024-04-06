import com.storyteller_f.version_manager.Versions
import com.storyteller_f.version_manager.baseLibrary
import com.storyteller_f.version_manager.commonAppDependency
import com.storyteller_f.version_manager.unitTestDependency

val versionManager: String by project

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.storyteller_f.version_manager")
}
android {
    namespace = "com.storyteller_f.plugin_core"
}
baseLibrary()

dependencies {
    commonAppDependency()
    unitTestDependency()
}