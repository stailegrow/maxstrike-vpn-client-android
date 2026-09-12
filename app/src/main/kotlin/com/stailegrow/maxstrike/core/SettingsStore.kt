package com.stailegrow.maxstrike.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Мелкие переключатели поведения из экрана "Настройки" (карточка
 * "// ПОВЕДЕНИЕ") — тот же принцип хранения, что у ThemeStore/RoutingStore:
 * SharedPreferences переживает перезапуск, значения текут наружу через
 * StateFlow.
 *
 *  - bypassLAN: обход локальной сети (принтеры/NAS/роутер доступны, пока
 *    VPN включён) — реально влияет на RoutingConfig.bypassLAN
 *    (см. RoutingStore.current()), раньше это было жёстко true.
 *  - pingOnLaunch: мерить пинг всех серверов при открытии приложения —
 *    влияет на то, что делает ServerStore.prepareOnLaunch().
 *  - liveBackground: лёгкая дышащая анимация фоновой сетки экрана — реально
 *    управляет ui/components/AppBackground.kt (за AppRoot в MainActivity).
 *  - excludedApps: раздельное туннелирование — пакеты, чей трафик не
 *    заходит в туннель (addDisallowedApplication в MaxStrikeVpnService).
 */
object SettingsStore {
    private const val PREFS_NAME = "max_strike_settings"
    private const val KEY_BYPASS_LAN = "bypass_lan"
    private const val KEY_PING_ON_LAUNCH = "ping_on_launch"
    private const val KEY_LIVE_BACKGROUND = "live_background"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_CUSTOM_DOMAINS = "custom_direct_domains"
    private const val KEY_EXCLUDED_APPS = "excluded_apps"

    private var prefs: SharedPreferences? = null

    private val _bypassLAN = MutableStateFlow(true)
    val bypassLAN: StateFlow<Boolean> = _bypassLAN.asStateFlow()

    private val _pingOnLaunch = MutableStateFlow(true)
    val pingOnLaunch: StateFlow<Boolean> = _pingOnLaunch.asStateFlow()

    private val _liveBackground = MutableStateFlow(true)
    val liveBackground: StateFlow<Boolean> = _liveBackground.asStateFlow()

    // Язык интерфейса (см. core/Localization.kt) — тот же принцип, что и
    // на маке (AppSettings.language): меняется сразу, L.current читают все
    // строки интерфейса, а полную перерисовку дерева делает key(language)
    // вокруг корня в MainActivity.kt.
    private val _language = MutableStateFlow(Lang.RU)
    val language: StateFlow<Lang> = _language.asStateFlow()

    // "Свои домены напрямую" (// МАРШРУТИЗАЦИЯ) — хранится одной строкой
    // через запятую, наружу отдаётся уже списком (RoutingStore.current()
    // подставляет его в RoutingConfig.directDomains).
    private val _customDirectDomains = MutableStateFlow<List<String>>(emptyList())
    val customDirectDomains: StateFlow<List<String>> = _customDirectDomains.asStateFlow()

    // "Исключённые приложения" (// МАРШРУТИЗАЦИЯ) — пакеты, чей трафик не
    // заходит в туннель. Хранится как и домены — строкой через запятую.
    private val _excludedApps = MutableStateFlow<Set<String>>(emptySet())
    val excludedApps: StateFlow<Set<String>> = _excludedApps.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        _bypassLAN.value = store.getBoolean(KEY_BYPASS_LAN, true)
        _pingOnLaunch.value = store.getBoolean(KEY_PING_ON_LAUNCH, true)
        _liveBackground.value = store.getBoolean(KEY_LIVE_BACKGROUND, true)
        val storedLanguage = store.getString(KEY_LANGUAGE, null)?.let { raw ->
            runCatching { Lang.valueOf(raw) }.getOrNull()
        }
        val language = storedLanguage ?: systemDefaultLanguage()
        _language.value = language
        L.current = language
        _customDirectDomains.value = parseDomains(store.getString(KEY_CUSTOM_DOMAINS, "") ?: "")
        _excludedApps.value = parseDomains(store.getString(KEY_EXCLUDED_APPS, "") ?: "").toSet()
    }

    private fun parseDomains(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Первый запуск начинается с языка системы — Android-аналог
     *  Lang.system на маке (Locale.preferredLanguages.first). */
    private fun systemDefaultLanguage(): Lang =
        if (java.util.Locale.getDefault().language.startsWith("ru")) Lang.RU else Lang.EN

    fun setBypassLAN(value: Boolean) {
        _bypassLAN.value = value
        prefs?.edit()?.putBoolean(KEY_BYPASS_LAN, value)?.apply()
    }

    fun setPingOnLaunch(value: Boolean) {
        _pingOnLaunch.value = value
        prefs?.edit()?.putBoolean(KEY_PING_ON_LAUNCH, value)?.apply()
    }

    fun setLiveBackground(value: Boolean) {
        _liveBackground.value = value
        prefs?.edit()?.putBoolean(KEY_LIVE_BACKGROUND, value)?.apply()
    }

    fun setLanguage(value: Lang) {
        _language.value = value
        L.current = value
        prefs?.edit()?.putString(KEY_LANGUAGE, value.name)?.apply()
    }

    /** Принимает как список, так и одну строку через запятую — на входе из
     *  диалога в Settings это всегда строка. */
    fun setCustomDirectDomains(raw: String) {
        val list = parseDomains(raw)
        _customDirectDomains.value = list
        prefs?.edit()?.putString(KEY_CUSTOM_DOMAINS, raw)?.apply()
    }

    fun customDirectDomainsRaw(): String = _customDirectDomains.value.joinToString(", ")

    fun setExcludedApps(packages: Set<String>) {
        _excludedApps.value = packages
        prefs?.edit()?.putString(KEY_EXCLUDED_APPS, packages.joinToString(","))?.apply()
    }
}
