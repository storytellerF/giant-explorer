import com.storyteller_f.version_manager.Versions

val versionManager: String by project

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("com.storyteller_f.song")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.storyteller_f.yue"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.storyteller_f.yue"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation(project(":yue-plugin"))
    //fixme
    constraints {
        listOf(
            "composite-compiler-ksp",
            "ext-func-compiler",
            "ui-list-annotation-compiler-ksp",
        ).forEach {
            ksp("${Versions.JITPACK_RELEASE_GROUP}:$it:$versionManager")
        }
    }
}

val userHome: String = System.getProperty("user.home")
val buildPath: String = layout.buildDirectory.asFile.get().absolutePath
song {
    transfers.set(listOf("$buildPath/intermediates/apk/debug/app-debug.apk"))
    adb.set(android.adbExecutable.absolutePath)
    paths.set(listOf())
    packages.set(listOf("com.storyteller_f.giant_explorer" to "files/plugins"))
    outputName.set("yue.apk")
}