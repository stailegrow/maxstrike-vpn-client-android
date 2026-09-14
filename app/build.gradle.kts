import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Ключ подписи релиза живёт вне репозитория (keystore.properties и сам
// keystore/release.keystore — в .gitignore). Файла нет — release-сборка
// просто останется неподписанной, это ожидаемо на чужой машине, где ключ
// ещё не сгенерирован.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.stailegrow.maxstrike"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.stailegrow.maxstrike"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
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

    // CameraX — предпросмотр и разбор кадров для сканера QR в диалоге
    // добавления сервера (core/CameraQRDecoder.kt,
    // ui/components/QrScannerView.kt). Версии актуальные на сентябрь
    // 2026 по доступным мне данным; Android Studio подскажет более новый
    // патч при синке, если появится.
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    // ZXing — чистый декодер QR без Google Play Services: приложение
    // распространяется APK-релизом на GitHub, без магазина (см.
    // PLAN-ANDROID.md), заводить ради одной фичи зависимость от GMS
    // (как потребовал бы ML Kit) ни к чему.
    implementation("com.google.zxing:core:3.5.3")

    // Юнит-тесты (обычный JVM, без эмулятора и телефона).
    testImplementation("junit:junit:4.13.2")
    // Android-заглушка org.json в юнит-тестах кидает исключение на любой
    // вызов — подменяем её настоящей реализацией только для теста.
    testImplementation("org.json:json:20260814")
}
