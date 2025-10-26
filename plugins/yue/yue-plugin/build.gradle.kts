import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.storyteller_f.yue_plugin"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    val javaVersion = JavaVersion.forClassVersion(libs.versions.jdk.get().toInt() + 44)
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
    lint {
        targetSdk = libs.versions.compileSdk.get().toInt()
    }
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jdk.get())
        optIn.add("kotlin.RequiresOptIn")
    }
}
dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.fragment.ktx)
    implementation(libs.activity.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    api(libs.giant.explorer.plugin.core)
    api(libs.lifecycle.runtime.ktx)
}
//dx 命令执行需要jdk8
val javaVersion: String = providers.exec {
    commandLine("java", "-version")
}.standardOutput.asText.get()
if (javaVersion.contains("\"1.8")) {
    val unpackAar = tasks.register<Copy>("unpackAar") {
        group = "gep"
        from(zipTree(layout.buildDirectory.file("outputs/aar/yue-plugin-debug.aar")))
        into(layout.buildDirectory.dir("intermediates/aar_unzip"))
    }
    val property = System.getProperty("os.name").orEmpty()
    val isWindows = property.lowercase().startsWith("win")
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
