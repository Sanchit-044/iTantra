package `in`.gov.itantra.core.pack

import `in`.gov.itantra.core.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LanguageInstallState(
    val busy: Boolean = false,
    val activeLanguages: Set<Language> = emptySet(),
    val progress: PackProgress? = null,
    val error: String? = null,
)

/**
 * Runs language-pack installation in a process-lifetime scope rather than a
 * screen's ViewModel scope, so the operator can hit Save on Language Setup or
 * Settings and immediately start using Talk / Alert / Radar while the actual
 * files keep downloading in the background. Supports queuing multiple language
 * downloads concurrently and pausing/cancelling them per-language or globally.
 */
class LanguagePackInstallCoordinator(
    private val packs: LanguagePackManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = LinkedHashSet<Language>()
    private val queueLock = Any()

    private val _state = MutableStateFlow(LanguageInstallState())
    val state: StateFlow<LanguageInstallState> = _state.asStateFlow()

    @Volatile
    private var job: Job? = null

    /**
     * Installs [languages] and uninstalls [remove] in the background.
     * Appends [languages] to the download queue so multiple selected languages
     * download sequentially without clobbering each other.
     */
    fun install(languages: Set<Language>, remove: Set<Language> = emptySet(), includeTranslation: Boolean = true) {
        synchronized(queueLock) {
            queue.addAll(languages)
            for (language in remove) {
                queue.remove(language)
            }
            updateStateLocked()
            startWorkerIfNeededLocked(includeTranslation)
        }
        if (remove.isNotEmpty()) {
            scope.launch {
                for (language in remove) {
                    try {
                        packs.uninstall(language)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /** Pauses/cancels download for [language], or all ongoing downloads if [language] is null. */
    fun pause(language: Language? = null) {
        synchronized(queueLock) {
            if (language == null) {
                queue.clear()
                job?.cancel()
                job = null
                _state.value = LanguageInstallState(busy = false, activeLanguages = emptySet())
            } else {
                val wasActive = queue.firstOrNull() == language
                queue.remove(language)
                if (wasActive) {
                    job?.cancel()
                    job = null
                    updateStateLocked()
                    startWorkerIfNeededLocked(includeTranslation = false)
                } else {
                    updateStateLocked()
                }
            }
        }
    }

    private fun updateStateLocked() {
        val busy = queue.isNotEmpty()
        _state.update { current ->
            current.copy(
                busy = busy,
                activeLanguages = queue.toSet(),
                progress = if (busy) current.progress else null,
            )
        }
    }

    private fun startWorkerIfNeededLocked(includeTranslation: Boolean) {
        if (job?.isActive == true || queue.isEmpty()) return
        val currentJob = scope.launch {
            while (isActive) {
                val nextLang = synchronized(queueLock) { queue.firstOrNull() } ?: break
                _state.update {
                    it.copy(
                        busy = true,
                        activeLanguages = synchronized(queueLock) { queue.toSet() },
                        progress = PackProgress(nextLang, 0f, "Downloading ${nextLang.englishName}…"),
                    )
                }
                var ok = false
                try {
                    packs.install(setOf(nextLang), includeTranslation) { progress ->
                        if (isActive) {
                            _state.update { it.copy(progress = progress) }
                        }
                    }
                    ok = true
                } catch (e: Exception) {
                    if (!isActive) break // Job was cancelled/paused
                    _state.update {
                        it.copy(
                            error = e.message?.takeIf { msg -> msg.isNotBlank() } ?: "Could not download ${nextLang.englishName}",
                        )
                    }
                }
                if (!isActive) break
                synchronized(queueLock) {
                    if (ok) {
                        queue.remove(nextLang)
                    }
                    updateStateLocked()
                }
            }
            synchronized(queueLock) {
                if (job === coroutineContext[Job]) {
                    job = null
                    _state.value = LanguageInstallState(busy = queue.isNotEmpty(), activeLanguages = queue.toSet())
                }
            }
        }
        job = currentJob
    }

    /** Dismisses a past failure without starting a new install. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
