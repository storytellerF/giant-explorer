import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    alias(libs.plugins.vanniktechPublish)
}

android {
    namespace = "com.storyteller_f.plugin_core"
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
    val javaVersion = JavaVersion.VERSION_21
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    lint {
        compileSdk = libs.versions.compileSdk.get().toInt()
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
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
}

val localUnsignedPublication = providers.gradleProperty("localUnsignedPublication")
    .map(String::toBoolean).getOrElse(false)

mavenPublishing {
    if (!localUnsignedPublication) {
        publishToMavenCentral()
        signAllPublications()
    }
    coordinates(
        groupId = "com.storyteller_f.giant_explorer",
        artifactId = "giant-explorer-plugin-core",
        version = project.version.toString(),
    )

    pom {
        name.set("giant-explorer-plugin-core")
        description.set("Giant Explorer Plugin Core Library")
        url.set("https://github.com/storytellerF/giant-explorer")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("storytellerF")
                name.set("storytellerF")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/storytellerF/giant-explorer.git")
            developerConnection.set("scm:git:ssh://github.com/storytellerF/giant-explorer.git")
            url.set("https://github.com/storytellerF/giant-explorer")
        }
    }
}
