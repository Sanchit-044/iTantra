package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.stt.EndpointTrigger
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.stt.SttException
import `in`.gov.itantra.core.stt.SttListener
import `in`.gov.itantra.core.stt.SttResult

/**
 * Speech-to-text capture for a spoken alert message: record, transcribe, hand the
 * recognized text back to the caller to send exactly like a typed alert (see
 * AlertScreen's "type a short alert" flow, which already carries it through
 * translate-on-receive and TTS on the other phone -- same as live PTT).
 *
 * Unlike [StartPttTransmissionUseCase] this never touches a [in.gov.itantra.core.transport.Transport],
 * requests no floor, and queues nothing itself: alerts already have their own delivery
 * path (SendAlertUseCase / broadcasters), so recording here is just STT.
 */
class RecordAlertMessageUseCase(
    private val sttEngine: SttEngine,
) {
    fun start(
        language: Language,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        if (sttEngine.activeLanguage != language) {
            AppLog.d("RecordAlertUseCase", "Loading STT model for language: $language")
            sttEngine.loadModel(language)
        }

        AppLog.d("RecordAlertUseCase", "Starting STT engine for alert recording")

        sttEngine.start(object : SttListener {
            override fun onPartial(text: String) {
                onPartialResult(text)
            }

            override fun onFinal(result: SttResult) {
                // A caller-initiated cancel() already reset the caller's own recording
                // state synchronously; this callback firing again asynchronously with
                // nothing useful to say would be redundant, not informative.
                if (result.trigger == EndpointTrigger.CANCELLED) {
                    AppLog.d("RecordAlertUseCase", "Recording cancelled")
                    return
                }
                // Blank (silence-only utterance) still reaches the caller so it can
                // reset its own "recording" UI state -- it just has nothing to send.
                onFinalResult(result.text.trim())
            }

            override fun onError(error: SttException) {
                AppLog.e("RecordAlertUseCase", "STT error: ${error.message}", error)
                onError(error.message ?: "Speech recognition failed")
            }
        })
    }

    /** Ends the utterance now and delivers whatever was recognized so far as final. */
    fun stop() = sttEngine.stop()

    /** Abandons the recording; no final result will be delivered. */
    fun cancel() = sttEngine.cancel()
}
