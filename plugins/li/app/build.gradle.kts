import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.storyteller_f.li"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.storyteller_f.li"
        minSdk = libs.versions.minSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }
    signingConfigs {
        val path = System.getenv("storyteller_f_sign_path")
        val alias = System.getenv("storyteller_f_sign_alias")
        val storePassword = System.getenv("storyteller_f_sign_store_password")
        val keyPassword = System.getenv("storyteller_f_sign_key_password")
        if (path != null && alias != null && storePassword != null && keyPassword != null) {
            create("release") {
                keyAlias = alias
                this.keyPassword = keyPassword
                storeFile = file(path)
                this.storePassword = storePassword
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue(
                "string",
                "leak_canary_display_activity_label",
                defaultConfig.applicationId?.substringAfterLast(".") ?: "Leaks"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSignConfig = signingConfigs.findByName("release")
            if (releaseSignConfig != null)
                signingConfig = releaseSignConfig
        }
    }
    val javaVersion = JavaVersion.forClassVersion(libs.versions.jdk.get().toInt() + 44)
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    dependenciesInfo {
        includeInBundle = false
        includeInApk = false
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
    "implementation"(libs.androidx.core.ktx)
    "implementation"(libs.androidx.appcompat)
    "implementation"(libs.material)
    "implementation"(libs.androidx.fragment.ktx)
    "implementation"(libs.androidx.activity.ktx)

    "debugImplementation"(libs.leakcanary.android)
    "implementation"(libs.androidx.multidex)
    "testImplementation"(libs.junit)
    "androidTestImplementation"(libs.androidx.junit)
    "androidTestImplementation"(libs.androidx.espresso.core)
    "implementation"(libs.androidx.navigation.fragment.ktx)
    "implementation"(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(project(":plugin"))
}
