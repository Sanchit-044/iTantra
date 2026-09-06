package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

class FakeSttEngine : SttEngine {
    var isListening = false
    override var activeLanguage: Language? = null
    var listener: SttListener? = null
    override var state: SttState = SttState.IDLE
    
    override fun unloadModel() {
        activeLanguage = null
        state = SttState.IDLE
    }

    override fun start(listener: SttListener) {
        isListening = true
        state = SttState.LISTENING
        this.listener = listener
    }

    override fun stop() {
        isListening = false
        state = SttState.MODEL_LOADED
        this.listener = null
    }

    override fun cancel() {
        isListening = false
        state = SttState.MODEL_LOADED
        this.listener = null
    }

    override fun loadModel(language: Language) {
        activeLanguage = language
    }

    override val silenceTimeoutMs: Long = 800L

    override fun close() {}
}
