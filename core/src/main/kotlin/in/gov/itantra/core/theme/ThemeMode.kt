package `in`.gov.itantra.core.theme

import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT = SYSTEM

        fun fromStored(raw: String?): ThemeMode {
            if (raw.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: DEFAULT
        }
    }
}

interface ThemeStore {
    val snapshot: ThemeMode
    val mode: StateFlow<ThemeMode>

    suspend fun setMode(mode: ThemeMode)
}
