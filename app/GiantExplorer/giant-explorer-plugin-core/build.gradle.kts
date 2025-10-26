import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
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

val env: MutableMap<String, String> = System.getenv()
group = group.takeIf { it.toString().contains(".") } ?: env["GROUP"] ?: "com.storyteller_f"
version = version.takeIf { it != "unspecified" } ?: env["VERSION"] ?: "0.0.1-local"

println("group: $group version: $version envGroup: ${env["GROUP"]} envVersion: ${env["VERSION"]}")
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                val component = components.find {
                    it.name == "java" || it.name == "release"
                }
                from(component)
            }
        }
    }
}