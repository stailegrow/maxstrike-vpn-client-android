import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.stailegrow.maxstrike"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.stailegrow.maxstrike"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Начиная с Kotlin 2.0 старый android.kotlinOptions{} — deprecated, в 2.4.x
// среда Android Studio уже не резолвит его вовсе. Актуальная замена — блок
// kotlin.compilerOptions{} на верхнем уровне файла, вне android{}.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

dependencies {
    // Версии BOM/activity/core-ktx — актуальные на сентябрь 2026, Android Studio
    // сама подскажет более новые патчи при синке, если появятся.
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
