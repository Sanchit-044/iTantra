package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

class FakeSttEngine : SttEngine {
    var isListening = false
    var activeLanguage: Language? = null
    var listener: SttListener? = null

    override fun start(listener: SttListener) {
        isListening = true
        this.listener = listener
    }

    override fun stop() {
        isListening = false
        this.listener = null
    }

    override fun loadModel(language: Language) {
        activeLanguage = language
    }

    override fun isModelAvailable(language: Language): Boolean = true
    override fun close() {}
}
