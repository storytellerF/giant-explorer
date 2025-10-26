// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        val navVersion = "2.7.5"
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:$navVersion")
        val songVersion = "2.0"
        val songFolder = project.findProperty("songFolder") as? String
        if (songFolder == "remote") {
            classpath("com.github.storytellerF.song:com.storyteller_f.song.gradle.plugin:$songVersion")
        } else if (songFolder == "local" || songFolder == "custom") {
            classpath("com.storyteller_f.song:plugin:$songVersion")
        }
    }
}

plugins {
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.4" apply false
}
