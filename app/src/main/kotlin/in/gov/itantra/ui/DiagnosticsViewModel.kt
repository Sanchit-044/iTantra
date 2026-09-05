package `in`.gov.itantra.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.android.diag.AndroidDiagnosticsService
import `in`.gov.itantra.core.diag.DiagnosticsSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val snapshot: DiagnosticsSnapshot? = null,
    val testMode: Boolean = false,
    val nextPrompt: String? = null,
    val werPairs: Int = 0,
    val shareError: String? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: AndroidDiagnosticsService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                diagnostics.probeRoundTrip()
                delay(POLL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun setTestMode(enabled: Boolean) {
        diagnostics.setWerTestMode(enabled)
        refresh()
    }

    fun share(context: Context) {
        val snapshot = diagnostics.snapshot()
        val json = snapshot.toJson()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "iTantra diagnostics ${snapshot.capturedAtMs}")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        try {
            val chooser = Intent.createChooser(send, "Share diagnostics JSON")
            if (context !is Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            _uiState.update { it.copy(shareError = null) }
        } catch (_: ActivityNotFoundException) {
            _uiState.update { it.copy(shareError = "No app available to share the JSON.") }
        } catch (e: Exception) {
            _uiState.update { it.copy(shareError = e.message ?: "Share failed") }
        }
    }

    fun clearShareError() {
        _uiState.update { it.copy(shareError = null) }
    }

    private fun refresh() {
        val snap = runCatching { diagnostics.snapshot() }.getOrNull()
        _uiState.update {
            it.copy(
                snapshot = snap ?: it.snapshot,
                testMode = diagnostics.testModeActive,
                nextPrompt = diagnostics.nextTestPrompt,
                werPairs = diagnostics.werPairCount,
            )
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private companion object {
        const val POLL_MS = 2_000L
    }
}
