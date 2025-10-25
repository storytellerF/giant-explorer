import com.storyteller_f.version_manager.Versions
import com.storyteller_f.version_manager.baseApp
import com.storyteller_f.version_manager.commonAppDependency
import com.storyteller_f.version_manager.constraintCommonUIListVersion
import com.storyteller_f.version_manager.navigationDependency
import com.storyteller_f.version_manager.unitTestDependency

val versionManager: String by project

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.storyteller_f.version_manager")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.storyteller_f.li"

    defaultConfig {
        applicationId = "com.storyteller_f.li"
        minSdk = 21
        versionCode = 1
        versionName = "1.0"

    }

    buildFeatures {
        viewBinding = true
    }
}
baseApp()
dependencies {
    commonAppDependency()
    unitTestDependency()
    navigationDependency()
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(project(":plugin"))
    //fixme
    listOf(
        "composite-compiler-ksp",
        "ext-func-compiler",
        "ui-list-annotation-compiler-ksp",
    ).forEach {
        ksp("${Versions.JITPACK_RELEASE_GROUP}:$it:$versionManager")
    }
}
constraintCommonUIListVersion(versionManager)
