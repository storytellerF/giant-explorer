import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.storyteller_f.version_manager.baseLibrary
import com.storyteller_f.version_manager.commonAndroidDependency
import com.storyteller_f.version_manager.unitTestDependency
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("com.storyteller_f.version_manager")
}

android {
    namespace = "com.storyteller_f.yue_plugin"

    defaultConfig {
        minSdk = 21
    }

}
baseLibrary()
dependencies {
    commonAndroidDependency()
    unitTestDependency()
    api(project(":giant-explorer-plugin-core"))
    api("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
}
//dx 命令执行需要jdk8
val javaVersion = providers.exec {
    commandLine("java", "-version")
}.standardOutput.asText.get()
if (javaVersion.contains("\"1.8")) {
    val unpackAar = tasks.register<Copy>("unpackAar") {
        group = "gep"
        from(zipTree(layout.buildDirectory.file("outputs/aar/yue-plugin-debug.aar")))
        into(layout.buildDirectory.dir("intermediates/aar_unzip"))
    }
    val property = System.getProperty("os.name").orEmpty()
    val isWindows = property.toLowerCaseAsciiOnly().startsWith("win")
    val convertJarToDex = tasks.register<Exec>("convertJarToDex") {
        group = "gep"
        val buildDir = layout.buildDirectory.asFile.get().absolutePath
        workingDir = File(buildDir, "intermediates/aar_unzip")
        val sdkDirectory = android.sdkDirectory.absolutePath
        val dxName = if (isWindows) "dx.bat" else "dx"
        commandLine = listOf(
            "$sdkDirectory/build-tools/30.0.2/$dxName",
            "--dex",
            "--output=classes.dex",
            "$buildDir/intermediates/aar_unzip/classes.jar"
        )
    }
    val packGep = tasks.register<Zip>("packGep") {
        group = "gep"
        archiveFileName.set("yue.gep")
        exclude("*.jar")
        destinationDirectory.set(layout.buildDirectory.dir("outputs/gep"))
        from(layout.buildDirectory.dir("intermediates/aar_unzip"))
    }

    packGep.dependsOn(convertJarToDex)
    convertJarToDex.dependsOn(unpackAar)
    unpackAar.dependsOn("bundleDebugAar")
    tasks.build {
        finalizedBy(packGep)
    }
}
