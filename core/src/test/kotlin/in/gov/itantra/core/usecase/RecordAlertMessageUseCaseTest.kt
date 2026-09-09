package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.stt.EndpointTrigger
import `in`.gov.itantra.core.stt.FakeSttEngine
import `in`.gov.itantra.core.stt.SttResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordAlertMessageUseCaseTest {

    @Test
    fun `final result is trimmed and delivered`() {
        val stt = FakeSttEngine()
        val useCase = RecordAlertMessageUseCase(stt)
        var delivered: String? = null

        useCase.start(Language.HINDI, onPartialResult = {}, onFinalResult = { delivered = it })
        stt.listener?.onFinal(SttResult("  need help  ", Language.HINDI, null, EndpointTrigger.SILENCE, 100, 0))

        assertEquals("need help", delivered)
    }

    @Test
    fun `blank result still reaches caller so recording state can reset`() {
        val stt = FakeSttEngine()
        val useCase = RecordAlertMessageUseCase(stt)
        var callCount = 0
        var delivered: String? = null

        useCase.start(Language.HINDI, onPartialResult = {}, onFinalResult = { callCount++; delivered = it })
        stt.listener?.onFinal(SttResult("   ", Language.HINDI, null, EndpointTrigger.SILENCE, 100, 0))

        assertEquals(1, callCount)
        assertEquals("", delivered)
    }

    @Test
    fun `cancelled result is dropped entirely`() {
        val stt = FakeSttEngine()
        val useCase = RecordAlertMessageUseCase(stt)
        var called = false

        useCase.start(Language.HINDI, onPartialResult = {}, onFinalResult = { called = true })
        stt.listener?.onFinal(SttResult("some text", Language.HINDI, null, EndpointTrigger.CANCELLED, 100, 0))

        assertTrue(!called)
    }

    @Test
    fun `partial results are forwarded as they arrive`() {
        val stt = FakeSttEngine()
        val useCase = RecordAlertMessageUseCase(stt)
        val partials = mutableListOf<String>()

        useCase.start(Language.HINDI, onPartialResult = { partials.add(it) }, onFinalResult = {})
        stt.listener?.onPartial("need")
        stt.listener?.onPartial("need help")

        assertEquals(listOf("need", "need help"), partials)
    }

    @Test
    fun `switches the resident model when a different language is requested`() {
        val stt = FakeSttEngine()
        stt.loadModel(Language.HINDI)
        val useCase = RecordAlertMessageUseCase(stt)

        useCase.start(Language.TAMIL, onPartialResult = {}, onFinalResult = {})

        assertEquals(Language.TAMIL, stt.activeLanguage)
    }
}
