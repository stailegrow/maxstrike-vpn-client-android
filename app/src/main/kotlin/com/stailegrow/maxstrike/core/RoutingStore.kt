package com.stailegrow.maxstrike.core

import android.content.Context
import android.content.SharedPreferences
import com.stailegrow.maxstrike.model.RoutingConfig
import com.stailegrow.maxstrike.model.RoutingPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Выбранный пресет маршрутизации — тот же принцип, что и у ThemeStore:
 * хранит только id в SharedPreferences (переживает перезапуск), а сама
 * RoutingConfig собирается по требованию через RoutingPreset.make().
 */
object RoutingStore {
    private const val PREFS_NAME = "max_strike_routing"
    private const val KEY_PRESET_ID = "preset_id"
    const val DEFAULT_PRESET_ID = "global"

    private var prefs: SharedPreferences? = null

    private val _presetID = MutableStateFlow(DEFAULT_PRESET_ID)
    val presetID: StateFlow<String> = _presetID.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        _presetID.value = store.getString(KEY_PRESET_ID, DEFAULT_PRESET_ID) ?: DEFAULT_PRESET_ID
    }

    fun select(id: String) {
        _presetID.value = id
        prefs?.edit()?.putString(KEY_PRESET_ID, id)?.apply()
    }

    fun current(): RoutingConfig {
        val preset = RoutingPreset.all.firstOrNull { it.id == _presetID.value } ?: RoutingPreset.global
        // bypassLAN раньше был всегда true (значение по умолчанию в самой
        // модели) — теперь это настоящий тумблер в "// ПОВЕДЕНИЕ"
        // (SettingsStore), здесь только подставляем его текущее значение.
        return preset.make().copy(
            bypassLAN = SettingsStore.bypassLAN.value,
            directDomains = SettingsStore.customDirectDomains.value,
        )
    }
}
