plugins {
    id("com.android.application")
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

    // При built-in Kotlin (AGP 9+) jvmTarget для Kotlin берётся отсюда же —
    // отдельно kotlin.compilerOptions{} задавать не нужно.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // AAR с собранным Go-рантаймом (libXray.aar) обычно тащит с собой
    // META-INF-файлы (лицензии и т.п.), которые конфликтуют с такими же
    // из других зависимостей — без этого сборка падает на дубликатах.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
            )
        }
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

    // Корутины — нужны сервису VPN (VpnService.Builder.establish(), запуск
    // ядра и т.п. — всё блокирующее, уходит на Dispatchers.IO).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Нативное ядро Xray-core (gomobile-сборка libXray). Файла может не быть,
    // пока не запущен Scripts/build-libxray.sh — тогда всё, что обращается к
    // XrayCoreBridge, не соберётся; это ожидаемо до первого запуска скрипта.
    implementation(files("libs/libXray.aar"))

    // Юнит-тесты (обычный JVM, без эмулятора и телефона).
    testImplementation("junit:junit:4.13.2")
    // Android-заглушка org.json в юнит-тестах кидает исключение на любой
    // вызов — подменяем её настоящей реализацией только для теста.
    testImplementation("org.json:json:20260814")
}
