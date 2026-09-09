package `in`.gov.itantra.theme

import android.content.Context
import `in`.gov.itantra.core.theme.ThemeMode
import `in`.gov.itantra.core.theme.ThemeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrefsThemeStore(
    context: Context,
) : ThemeStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(read())
    override val snapshot: ThemeMode get() = _mode.value
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override suspend fun setMode(mode: ThemeMode) {
        val next = mode
        prefs.edit().putString(KEY_MODE, next.name).apply()
        _mode.value = next
    }

    private fun read(): ThemeMode =
        ThemeMode.fromStored(prefs.getString(KEY_MODE, null))

    companion object {
        const val PREFS_NAME = "itantra_theme"
        const val KEY_MODE = "theme_mode"
    }
}
