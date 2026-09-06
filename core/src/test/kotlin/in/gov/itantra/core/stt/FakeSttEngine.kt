package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

class FakeSttEngine : SttEngine {
    var isListening = false
    override var activeLanguage: Language? = null
    var listener: SttListener? = null
    override val state: SttState get() = if (isListening) SttState.LISTENING else SttState.IDLE
    override val silenceTimeoutMs: Long get() = 800L

    override fun start(listener: SttListener) {
        isListening = true
        this.listener = listener
    }

    override fun stop() {
        isListening = false
        this.listener = null
    }

    override fun cancel() {
        isListening = false
        this.listener = null
    }

    override fun loadModel(language: Language) {
        activeLanguage = language
    }

    override fun unloadModel() {
        activeLanguage = null
    }

    override fun close() {}
}
