package com.stailegrow.maxstrike.core

/**
 * Язык интерфейса — Android-аналог Core/Localization.swift на маке
 * (enum Lang + enum L). Там и держится настоящее объяснение подхода:
 * пара «оригинал — перевод» пишется прямо на месте, где строка нужна
 * (L.t("Закрыть", "Close")), а не в отдельной таблице ключей — на бумаге
 * такая таблица выглядит аккуратнее, но на практике расходится с
 * интерфейсом: ключ теряет смысл, перевод отстаёт, а пустой ключ виден
 * только в готовой сборке. Строка, привязанная к переводу текстом рядом,
 * просто негде забыть непереведённой.
 */
enum class Lang {
    RU, EN;

    val title: String
        get() = when (this) {
            RU -> "Русский"
            EN -> "English"
        }
}

/**
 * L.current — обычное изменяемое поле, не StateFlow: как и на маке
 * (`nonisolated(unsafe) static var current`), оно не обязано само по
 * себе быть источником рекомпозиции Compose — за то, чтобы весь экран
 * перерисовался при смене языка, отвечает `key(language) { … }` вокруг
 * корня дерева в MainActivity.kt, а не наблюдение за этим полем. Менять
 * язык (и, следовательно, current) можно только через
 * SettingsStore.setLanguage() — она же хранит выбор на диске и держит
 * StateFlow, за которым Compose уже действительно наблюдает.
 */
object L {
    var current: Lang = Lang.RU

    fun t(ru: String, en: String): String = if (current == Lang.RU) ru else en
}
