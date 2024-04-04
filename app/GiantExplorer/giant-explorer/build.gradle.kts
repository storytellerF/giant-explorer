import com.android.build.api.dsl.VariantDimension
import com.storyteller_f.version_manager.Versions
import com.storyteller_f.version_manager.baseApp
import com.storyteller_f.version_manager.constraintCommonUIListVersion
import com.storyteller_f.version_manager.implModule
import com.storyteller_f.version_manager.networkDependency
import com.storyteller_f.version_manager.setupGeneric
import com.storyteller_f.version_manager.setupPreviewFeature
import com.storyteller_f.version_manager.workerDependency

val versionManager: String by project

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("com.storyteller_f.version_manager")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
//    id("com.starter.easylauncher")
    id("androidx.room")
}

android {

    val id = "com.storyteller_f.giant_explorer"
    defaultConfig {
        applicationId = id
    }

    namespace = "com.storyteller_f.giant_explorer"
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

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
}

dependencies {

    implementation(libs.constraintlayout)
    networkDependency()
    workerDependency()

    handleSu()
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

baseApp()
implModule(":slim-ktx")
constraintCommonUIListVersion(versionManager)
configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("com.google.guava:listenablefuture") {
        select("com.google.guava:guava:0")
    }
}
setupGeneric()
setupPreviewFeature()

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

fun DependencyHandlerScope.handleSu() {
    val libsuVersion = "5.0.3"

    // The core module that provides APIs to a shell
    implementation("com.github.topjohnwu.libsu:core:${libsuVersion}")

    // Optional: APIs for creating root services. Depends on ":core"
    implementation("com.github.topjohnwu.libsu:service:${libsuVersion}")

    // Optional: Provides remote file system support
    implementation("com.github.topjohnwu.libsu:nio:${libsuVersion}")
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