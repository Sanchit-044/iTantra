package `in`.gov.itantra.lang

import android.content.Context
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.LanguageSelection
import `in`.gov.itantra.core.lang.LanguageSettings
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.lang.UiLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PrefsLanguageSettingsStore(
    context: Context,
) : LanguageSettingsStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    override val snapshot: LanguageSettings get() = _settings.value
    override val settings: StateFlow<LanguageSettings> = _settings.asStateFlow()

    override suspend fun completeSetup(
        installed: Set<Language>,
        current: Language,
        uiLanguage: Language,
    ) {
        write(setupDone = true, installed = installed, current = current, uiLanguage = uiLanguage)
    }

    override suspend fun updateSelection(
        installed: Set<Language>,
        current: Language,
        uiLanguage: Language,
    ) {
        write(setupDone = true, installed = installed, current = current, uiLanguage = uiLanguage)
    }

    override suspend fun setCurrentLanguage(language: Language) {
        val snap = _settings.value
        write(
            setupDone = snap.setupDone,
            installed = snap.installed,
            current = language,
            uiLanguage = snap.uiLanguage,
        )
    }

    override suspend fun setUiLanguage(language: Language) {
        val snap = _settings.value
        write(
            setupDone = snap.setupDone,
            installed = snap.installed,
            current = snap.current,
            uiLanguage = language,
        )
    }

    private fun read(): LanguageSettings {
        val setupDone = prefs.getBoolean(KEY_SETUP, false)
        val installed = prefs.getString(KEY_INSTALLED, null)
            ?.split(',')
            ?.mapNotNull { Language.fromCode(it.trim()) }
            ?.toSet()
            .orEmpty()
        val current = prefs.getString(KEY_CURRENT, null)?.let { Language.fromCode(it) }
        val uiLanguage = prefs.getString(KEY_UI, null)?.let { Language.fromCode(it) }
        return LanguageSettings(
            setupDone = setupDone,
            installed = LanguageSelection.normalizeInstalled(installed),
            current = LanguageSelection.normalizeCurrent(current, installed),
            uiLanguage = UiLanguage.normalize(uiLanguage, installed),
        )
    }

    private fun write(
        setupDone: Boolean,
        installed: Set<Language>,
        current: Language,
        uiLanguage: Language,
    ) {
        val next = LanguageSettings(
            setupDone = setupDone,
            installed = installed,
            current = current,
            uiLanguage = uiLanguage,
        ).normalized
        prefs.edit()
            .putBoolean(KEY_SETUP, next.setupDone)
            .putString(KEY_INSTALLED, next.installed.joinToString(",") { it.code })
            .putString(KEY_CURRENT, next.current.code)
            .putString(KEY_UI, next.uiLanguage.code)
            .apply()
        _settings.update { next }
    }

    companion object {
        private const val PREFS_NAME = "itantra_language"
        private const val KEY_SETUP = "setup_done"
        private const val KEY_INSTALLED = "installed"
        private const val KEY_CURRENT = "current"
        private const val KEY_UI = "ui_language"
    }
}
