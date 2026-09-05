package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioFormat
import `in`.gov.itantra.core.audio.AudioSink
import `in`.gov.itantra.core.audio.AudioSinkFactory
import `in`.gov.itantra.core.audio.RecordingSink
import `in`.gov.itantra.core.translate.DictionaryTranslationEngine
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.tts.FakeTtsEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceivePttTransmissionUseCaseTest {

    @Test
    fun `same language skips translation`() = runBlocking {
        val tts = FakeTtsEngine()
        val sink = RecordingSink()
        val useCase = ReceivePttTransmissionUseCase(
            ttsEngine = tts,
            audioSinkFactory = factory(sink),
            translationEngine = DictionaryTranslationEngine(),
        )
        useCase.execute(
            Packet.text(MessageType.NORMAL, Language.HINDI, 1, "पानी बढ़ रहा है"),
            currentLanguage = Language.HINDI,
        )
        assertEquals(listOf("पानी बढ़ रहा है"), tts.synthesisedChunks)
        assertEquals(Language.HINDI, tts.activeLanguage)
        assertTrue(sink.sampleCount > 0)
    }

    @Test
    fun `hindi packet is spoken as tamil for a tamil listener`() = runBlocking {
        val tts = FakeTtsEngine()
        val useCase = ReceivePttTransmissionUseCase(
            ttsEngine = tts,
            audioSinkFactory = factory(RecordingSink()),
            translationEngine = DictionaryTranslationEngine(),
        )
        useCase.execute(
            Packet.text(MessageType.NORMAL, Language.HINDI, 1, "मुझे मदद चाहिए"),
            currentLanguage = Language.TAMIL,
        )
        assertEquals(listOf("எனக்கு உதவி வேண்டும்"), tts.synthesisedChunks)
        assertEquals(Language.TAMIL, tts.activeLanguage)
    }

    private fun factory(sink: RecordingSink) = object : AudioSinkFactory {
        override fun createSink(format: AudioFormat): AudioSink = sink
    }
}
