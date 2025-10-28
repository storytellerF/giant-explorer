buildscript {
    dependencies {
        classpath(libs.com.storyteller.f.jksify.gradle.plugin)
    }
}

plugins {
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.4" apply false
}
