package com.wisekitchen.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { LIGHT("Light"), DARK("Dark"), SYSTEM("Match my phone") }

/** The user's choices, kept between launches. */
class Settings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _textScale = MutableStateFlow(prefs.getFloat("textScale", DEFAULT_TEXT_SCALE).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE))
    /** How much bigger than normal recipe text is drawn. */
    val textScale: StateFlow<Float> = _textScale

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.LIGHT),
    )
    val theme: StateFlow<ThemeMode> = _theme

    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _autoUpdate = MutableStateFlow(prefs.getBoolean("autoUpdate", true))
    /** Check GitHub for new recipes and app updates by itself. */
    val autoUpdate: StateFlow<Boolean> = _autoUpdate

    fun setTextScale(v: Float) {
        val s = (Math.round(v * 20) / 20f).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        _textScale.value = s
        prefs.edit().putFloat("textScale", s).apply()
    }

    fun biggerText() = setTextScale(_textScale.value + TEXT_STEP)
    fun smallerText() = setTextScale(_textScale.value - TEXT_STEP)

    fun setTheme(t: ThemeMode) {
        _theme.value = t
        prefs.edit().putString("theme", t.name).apply()
    }

    fun toggleFavorite(id: String) {
        val now = _favorites.value.let { if (id in it) it - id else it + id }
        _favorites.value = now
        prefs.edit().putStringSet("favorites", now).apply()
    }

    fun setAutoUpdate(on: Boolean) {
        _autoUpdate.value = on
        prefs.edit().putBoolean("autoUpdate", on).apply()
    }

    companion object {
        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 2.6f
        const val DEFAULT_TEXT_SCALE = 1.25f
        const val TEXT_STEP = 0.15f
    }
}
