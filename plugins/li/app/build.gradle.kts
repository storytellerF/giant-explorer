import com.storyteller_f.version_manager.*

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.storyteller_f.version_manager")
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
    commonAndroidDependency()
    unitTestDependency()
    navigationDependency()
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(project(":plugin"))
}
