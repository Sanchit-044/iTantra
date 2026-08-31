package `in`.gov.itantra.harness

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.WavCodec
import `in`.gov.itantra.core.eval.CorpusItem
import `in`.gov.itantra.core.eval.OfflineTranscriber
import `in`.gov.itantra.core.eval.SttEvaluationHarness
import `in`.gov.itantra.core.stt.ModelRegistry
import `in`.gov.itantra.core.stt.SttBackend
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Module B2 -- headless STT evaluation across Hindi, Tamil and Bengali.
 *
 * Run with:
 *   ./gradlew :harness:connectedAndroidTest
 *
 * Corpus layout, under `harness/src/androidTest/assets/corpus/`:
 *
 *     corpus/<lang>/<id>.wav        16 kHz mono 16-bit PCM
 *     corpus/<lang>/<id>.txt        UTF-8 reference transcript
 *
 * where <lang> is hi, ta or bn. The corpus is intentionally NOT committed: field
 * recordings of real speakers are personal data, and checking them into a public
 * repository is not something to do casually. See docs/CORPUS.md for how to assemble
 * one.
 *
 * This test reports numbers; it does not assert a WER threshold. Asserting a target
 * nobody has measured yet would either fail meaninglessly or, worse, be set loose
 * enough to always pass. The findings list is what escalates.
 */
@RunWith(AndroidJUnit4::class)
class SttEvaluationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun evaluateAllLanguages() {
        val corpus = loadCorpus()
        assumeTrue(
            "No evaluation corpus bundled -- see docs/CORPUS.md. Skipping rather than " +
                "reporting a fabricated score.",
            corpus.isNotEmpty(),
        )

        val backend = selectedBackend()
        val transcriber = engineTranscriber(backend)

        val report = SttEvaluationHarness(transcriber).evaluate(corpus)
        val text = report.format()

        // Logged in chunks: logcat truncates a single very long line.
        text.lineSequence().forEach { Log.i(TAG, it) }

        // Also written to the device so it can be pulled off with adb rather than
        // scraped out of logcat.
        File(context.getExternalFilesDir(null), "b2-stt-evaluation.txt").writeText(text)

        val findings = report.findings()
        if (findings.isNotEmpty()) {
            Log.w(TAG, "=== ${findings.size} finding(s) require escalation ===")
            findings.forEach { Log.w(TAG, it) }
        }
    }

    /**
     * Which backend to evaluate. There is currently only one; the indirection stays so
     * that adding a second backend to compare against costs nothing here.
     */
    private fun selectedBackend(): SttBackend {
        val arg = InstrumentationRegistry.getArguments().getString("itantra.stt.backend")
        return SttBackend.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) }
            ?: SttBackend.ONNX_CTC
    }

    /**
     * Adapts whichever engine is under test to [OfflineTranscriber].
     *
     * NOTE: the file-decoding entry point is not the same code path as the live
     * microphone path -- it bypasses capture and the endpointer. That is correct for
     * measuring acoustic-model accuracy, but it means a good B2 score does not by
     * itself validate the live pipeline. Module B4 covers that.
     */
    private fun engineTranscriber(backend: SttBackend): OfflineTranscriber =
        object : OfflineTranscriber {
            override val supportedLanguages: Set<Language> =
                ModelRegistry.supportedLanguages(backend)

            override fun transcribe(clip: AudioClip, language: Language): String {
                val decoder = FileSttDecoder.forBackend(backend, context)
                return decoder.use { it.decode(clip, language) }
            }
        }

    private fun loadCorpus(): List<CorpusItem> {
        val out = mutableListOf<CorpusItem>()
        for (language in Language.entries) {
            val dir = "corpus/${language.code}"
            val files = runCatching { context.assets.list(dir) }.getOrNull() ?: continue
            for (name in files.filter { it.endsWith(".wav") }) {
                val id = name.removeSuffix(".wav")
                val referencePath = "$dir/$id.txt"
                val reference = runCatching {
                    context.assets.open(referencePath).use {
                        it.readBytes().toString(Charsets.UTF_8).trim()
                    }
                }.getOrNull()

                if (reference.isNullOrBlank()) {
                    Log.w(TAG, "skipping $dir/$name: no reference transcript at $referencePath")
                    continue
                }

                val audio = context.assets.open("$dir/$name").use {
                    WavCodec.decode(it.readBytes(), name)
                }
                require(audio.format.sampleRate == 16_000) {
                    "$dir/$name is ${audio.format.sampleRate} Hz; the corpus must be 16 kHz mono"
                }
                out += CorpusItem(id, language, audio, reference)
            }
        }
        return out
    }

    private companion object {
        const val TAG = "iTantra.B2"
    }
}
