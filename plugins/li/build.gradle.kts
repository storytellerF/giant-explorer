buildscript {
    dependencies {
        classpath(libs.jksify.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.devtools.ksp) apply false
}
