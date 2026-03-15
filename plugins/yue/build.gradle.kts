buildscript {
    dependencies {
        classpath(libs.com.storyteller.f.song.gradle.plugin)
        classpath(libs.jksify.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.safeArgs) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.ksp) apply false
}
