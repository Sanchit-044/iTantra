package `in`.gov.itantra.android.stt

import android.content.Context
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.stt.SttBackend

/**
 * Decodes a complete audio clip to text, bypassing the microphone.
 *
 * This is the entry point Module B2's evaluation harness drives. It deliberately does
 * NOT go through the live capture path or the endpointer: B2 is measuring acoustic-model
 * accuracy, and mixing in capture behaviour would confound the number. The live path is
 * what Module B4 exercises.
 */
interface FileSttDecoder : AutoCloseable {

    fun decode(clip: AudioClip, language: Language): String

    companion object {
        fun forBackend(backend: SttBackend, context: Context): FileSttDecoder = when (backend) {
            SttBackend.ONNX_CTC -> OnnxCtcFileDecoder(context)
        }
    }
}

/** IndicWav2Vec CTC offline decoding. Covers all three languages. */
class OnnxCtcFileDecoder(context: Context) : FileSttDecoder {

    private val decoder = OnnxCtcDecoder(context)

    override fun decode(clip: AudioClip, language: Language): String {
        require(clip.format.sampleRate == SAMPLE_RATE) {
            "wav2vec2 expects $SAMPLE_RATE Hz, got ${clip.format.sampleRate}"
        }
        return decoder.decode(clip, language)
    }

    override fun close() = decoder.close()

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
