import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

buildscript {
    dependencies {
        val versionManager: String by project
        classpath(libs.navigation.safe.args.gradle.plugin)
        //jitpack 构建
        classpath("com.github.storytellerF.common-ui-list:version-manager:$versionManager")
        //本地构建
//        classpath("com.storyteller_f:version-manager:0.0.1-local")
    }
}
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.ksp) apply false
    id("com.starter.easylauncher") version "6.3.0" apply false
    id("androidx.room") version "2.6.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    detekt {
        source.setFrom(
            io.gitlab.arturbosch.detekt.extensions.DetektExtension.DEFAULT_SRC_DIR_JAVA,
            io.gitlab.arturbosch.detekt.extensions.DetektExtension.DEFAULT_TEST_SRC_DIR_JAVA,
            io.gitlab.arturbosch.detekt.extensions.DetektExtension.DEFAULT_SRC_DIR_KOTLIN,
            io.gitlab.arturbosch.detekt.extensions.DetektExtension.DEFAULT_TEST_SRC_DIR_KOTLIN,
        )
        buildUponDefaultConfig = true
        autoCorrect = true
        config.setFrom("$rootDir/config/detekt/detekt.yml")
        baseline = file("$rootDir/config/detekt/baseline.xml")
    }
    dependencies {
        val detektVersion = "1.23.1"

        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-ruleauthors:$detektVersion")
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "1.8"
        basePath = rootDir.absolutePath
    }
    tasks.withType<DetektCreateBaselineTask>().configureEach {
        jvmTarget = "1.8"
    }

}