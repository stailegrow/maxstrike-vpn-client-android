// Версии — актуальные стабильные на момент написания (сентябрь 2026).
// При первом синке Android Studio может предложить более свежие патчи —
// это нормально, соглашайся.
//
// AGP 9.0+ несёт Kotlin-поддержку внутри себя ("built-in Kotlin") и сама
// компилирует .kt-файлы — отдельный плагин org.jetbrains.kotlin.android
// с ней несовместим (именно на этом упал синк). Оставляем только AGP и
// плагин компилятора Compose.
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
