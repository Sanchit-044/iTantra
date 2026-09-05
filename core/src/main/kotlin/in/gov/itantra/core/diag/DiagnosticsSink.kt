package `in`.gov.itantra.core.diag

import `in`.gov.itantra.core.Language

/**
 * Optional hook from the PTT path into Module B7.
 *
 * Kept in :core so the use-case can record without depending on Android. A no-op
 * implementation is safe: diagnostics must never change send/receive behaviour.
 */
interface DiagnosticsSink {
    fun onSttFinal(
        text: String,
        language: Language,
        finalisationMs: Long,
        audioMs: Long,
        cancelled: Boolean,
    )
}
