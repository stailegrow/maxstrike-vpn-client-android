package com.stailegrow.maxstrike.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Выбранная палитра — Android-аналог EnvironmentValues.palette на macOS,
 * только хранится ещё и на диске (SharedPreferences), а не только в памяти
 * окна: тема должна пережить перезапуск приложения. Сама палитра по id
 * резолвится в ui/theme (см. Palette.kt, LocalPalette в Theme.kt) — этот
 * объект в core намеренно ничего не знает про Compose и про сами цвета.
 */
object ThemeStore {
    private const val PREFS_NAME = "max_strike_theme"
    private const val KEY_PALETTE_ID = "palette_id"
    const val DEFAULT_PALETTE_ID = "amber"

    private var prefs: SharedPreferences? = null

    private val _paletteId = MutableStateFlow(DEFAULT_PALETTE_ID)
    val paletteId: StateFlow<String> = _paletteId.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        _paletteId.value = store.getString(KEY_PALETTE_ID, DEFAULT_PALETTE_ID) ?: DEFAULT_PALETTE_ID
    }

    fun select(id: String) {
        _paletteId.value = id
        prefs?.edit()?.putString(KEY_PALETTE_ID, id)?.apply()
    }
}
