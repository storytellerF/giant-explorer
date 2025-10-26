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

// 平台判断
val isWindows = System.getProperty("os.name").lowercase().startsWith("win")

// 1️⃣ 解压 AAR
val unpackAar = tasks.register<Copy>("unpackAar") {
    group = "gep"
    val aarFile = layout.buildDirectory.file("outputs/aar/yue-plugin-debug.aar")
    from(zipTree(aarFile))
    into(layout.buildDirectory.dir("intermediates/aar_unzip"))
}

// 2️⃣ 转换 classes.jar → classes.dex（用 D8）
val convertJarToDex = tasks.register<Exec>("convertJarToDex") {
    group = "gep"
    dependsOn(unpackAar)

    val buildDirPath = layout.buildDirectory.asFile.get()
    val unzipDir = File(buildDirPath, "intermediates/aar_unzip")
    val sdkDirectory = android.sdkDirectory
    val buildToolsVersion = android.buildToolsVersion
    val d8Name = if (isWindows) "d8.bat" else "d8"

    val d8Path = File(sdkDirectory, "build-tools/$buildToolsVersion/$d8Name")
    if (!d8Path.exists()) {
        throw GradleException("❌ D8 not found: ${d8Path.absolutePath}")
    }

    workingDir = unzipDir
    commandLine(
        d8Path.absolutePath,
        "--output", unzipDir.absolutePath,
        File(unzipDir, "classes.jar").absolutePath
    )
}

// 3️⃣ 打 gep 包
val packGep = tasks.register<Zip>("packGep") {
    group = "gep"
    dependsOn(convertJarToDex)

    archiveFileName.set("yue.gep")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/gep"))
    exclude("*.jar")
    from(layout.buildDirectory.dir("intermediates/aar_unzip"))
}

packGep.dependsOn(convertJarToDex)
convertJarToDex.dependsOn(unpackAar)
unpackAar.dependsOn("bundleDebugAar")
tasks.build {
    finalizedBy(packGep)
}