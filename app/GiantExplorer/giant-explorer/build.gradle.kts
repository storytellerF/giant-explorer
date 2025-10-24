import com.android.build.api.dsl.VariantDimension
import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")

    id("kotlin-kapt")
    id("com.google.devtools.ksp")
    id("com.starter.easylauncher")
    id("androidx.room")
    alias(libs.plugins.kotlinCompose)
}

android {
    val id = "com.storyteller_f.giant_explorer"
    namespace = "com.storyteller_f.giant_explorer"
    buildTypes {
        debug {
            val suffix = ".$name"
            registerProviderKey("file-provider", id, suffix)
            registerProviderKey("file-system-provider", id, suffix)
            registerProviderKey("file-system-encrypted-provider", id, suffix)
        }
        release {
            registerProviderKey("file-provider", id)
            registerProviderKey("file-system-provider", id)
            registerProviderKey("file-system-encrypted-provider", id)
        }
    }
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = id
        minSdk = libs.versions.minSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        targetSdk = libs.versions.compileSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    val javaVersion = JavaVersion.VERSION_21
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    dependenciesInfo {
        includeInBundle = false
        includeInApk = false
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
}
dependencies {
    implementation(libs.startup)
    implementation(libs.slim.ktx)
    implementation(libs.common.ktx)
    implementation(libs.compat.ktx)
    implementation(libs.common.ui)
    implementation(libs.common.pr)
    implementation(libs.ui.list)
    implementation(libs.ui.list.annotation.definition)
    ksp(libs.ui.list.annotation.compiler.ksp)
    implementation(libs.composite.definition)
    ksp(libs.composite.compiler.ksp)
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.fragment.ktx)
    implementation(libs.activity.ktx)

    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.leakcanary.android)
    implementation(libs.androidx.multidex)
    implementation(libs.slim.ktx)
    implementation(libs.androidx.material)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.view.holder.compose)
    ksp(libs.ext.func.compiler)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.constraintlayout)
    implementation(libs.retrofit)
    implementation(libs.retrofit.mock)
    implementation(libs.logging.interceptor)
    implementation(libs.androidx.work.runtime.ktx)
    androidTestImplementation(libs.androidx.work.testing)
    implementation(libs.androidx.work.multiprocess)

    // The core module that provides APIs to a shell
    implementation(libs.core)
    // Optional: APIs for creating root services. Depends on ":core"
    implementation(libs.service)
    // Optional: Provides remote file system support
    implementation(libs.nio)
    handleShun()
    implementation(project(":giant-explorer-plugin-core"))

    implementation(libs.simplemagic)
    implementation(libs.glide)

    implementation(libs.browser)
    implementation(libs.webkit)
    implementation(libs.preference.ktx)
    implementation(libs.window)

    androidTestImplementation(libs.room.testing)

    val liPluginModule = findProject(":li-plugin")
    if (liPluginModule != null) {
        implementation(liPluginModule)
    }
    implementation(libs.logback.android)

    implementation(libs.file.system.remote)
    implementation(libs.file.system.ktx)
    implementation(libs.file.system)
    implementation(libs.file.system.root)
    implementation(libs.file.system.archive)
    implementation(libs.file.system.memory)
    implementation(libs.file.system.local)
    implementation(libs.lifecycle.service)
}
configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("com.google.guava:listenablefuture") {
        select("com.google.guava:guava:0")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
        jvmTarget = JvmTarget.JVM_21
        optIn = listOf("kotlin.RequiresOptIn")
    }
}

fun DependencyHandlerScope.handleShun() {
    //filter & sort
    val filterArtifact = listOf("config-core", "sort-core", "filter-core", "filter-ui", "sort-ui")

    val filterModules = filterArtifact.mapNotNull {
        findProject(":filter:$it")
    }
    if (filterModules.size == filterArtifact.size) {
        filterModules.forEach {
            implementation(it)
        }
    } else {
        filterArtifact.forEach {
            implementation("com.github.storytellerF.Shun:$it:1.0.0")
        }
    }
}

/**
 * 同时设置manifest和buildConfig
 */
fun VariantDimension.registerProviderKey(
    identification: String,
    applicationId: String?,
    valueSuffix: String? = null
) {
    val placeholderKey = placeholderKey(identification)
    val buildConfigKey = buildConfigKey(placeholderKey)
    val authorityValue =
        "${applicationId}.$identification${if (valueSuffix == null) "" else "$valueSuffix"}"

    // Now we can use ${documentsAuthority} in our Manifest
    manifestPlaceholders[placeholderKey] = authorityValue
    // Now we can use BuildConfig.DOCUMENTS_AUTHORITY in our code
    buildConfigField(
        "String",
        buildConfigKey.toString(),
        "\"${authorityValue}\""
    )
}

fun buildConfigKey(placeholderKey: String): StringBuilder {
    val configKey = StringBuilder()
    placeholderKey.forEachIndexed { index, c ->
        configKey.append(
            when {
                index == 0 -> c.uppercase()
                c.isUpperCase() && placeholderKey[index - 1].isLowerCase() -> "_$c"
                else -> c.uppercase()
            }
        )
    }
    return configKey
}

fun placeholderKey(identification: String): String {
    val identifyString = StringBuilder()
    var i = 0
    while (i < identification.length) {
        val c = identification[i++]
        if (c == '-') {
            identifyString.append(identification[i++].uppercase())
        } else {
            identifyString.append(c)
        }
    }
    return identifyString.append("Authority").toString()
}

room {
    schemaDirectory("$projectDir/schemas")
}