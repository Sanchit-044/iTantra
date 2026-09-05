package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelRegistryTest {

    /**
     * The requirement that drove the backend decision: whatever backend ships must
     * cover all three languages in scope.
     *
     * This is the test that Vosk failed -- it has no Tamil or Bengali acoustic model,
     * so it could serve only Hindi. Written as a property of "the backend" rather than
     * of a specific one, so any future backend swap has to satisfy it too.
     * See docs/STT-BACKEND.md.
     */
    @Test
    fun `every backend covers all three languages in scope`() {
        for (backend in SttBackend.entries) {
            assertTrue(
                ModelRegistry.missingLanguages(backend).isEmpty(),
                "$backend cannot serve ${ModelRegistry.missingLanguages(backend)} -- " +
                    "a backend that covers only part of the language scope must not ship",
            )
            assertEquals(
                Language.entries.toSet(),
                ModelRegistry.supportedLanguages(backend),
                "$backend does not cover the full language scope",
            )
        }
    }

    @Test
    fun `a descriptor exists for every language`() {
        for (language in Language.entries) {
            assertTrue(
                ModelRegistry.descriptor(SttBackend.ONNX_CTC, language) != null,
                "no model descriptor for ${language.code}",
            )
        }
    }

    @Test
    fun `every declared model has a distinct asset path`() {
        val paths = ModelRegistry.ONNX_CTC.values.map { it.assetPath }
        assertEquals(paths.size, paths.toSet().size, "two models share an asset path")
    }

    @Test
    fun `no asset path points outside the bundled assets directory`() {
        // Guards the offline constraint: nothing may resolve to a URL or an external path.
        for (m in ModelRegistry.ONNX_CTC.values) {
            assertTrue(m.assetPath.startsWith("models/"), "unexpected asset root: ${m.assetPath}")
            assertTrue(!m.assetPath.contains("://"), "asset path looks like a URL: ${m.assetPath}")
            assertTrue(!m.assetPath.contains(".."), "asset path escapes its root: ${m.assetPath}")
        }
    }
}

class MemoryBudgetTest {

    @Test
    fun `one resident ctc model plus one vits voice fits the budget`() {
        for (language in Language.entries) {
            val a = MemoryBudget.assess(
                sttModel = ModelRegistry.ONNX_CTC[language],
                ttsModelBytes = MemoryBudget.VITS_INT8_ESTIMATE_BYTES,
            )
            assertTrue(
                a.withinBudget,
                "${language.code} does not fit the 2 GB device budget: ${a.describe()}",
            )
        }
    }

    /**
     * Why the one-model-at-a-time rule is load-bearing rather than tidy design.
     * Holding all three models resident blows the budget, so the unload-before-load
     * behaviour in every model holder is what keeps the app alive on the target device.
     */
    @Test
    fun `holding all three models resident would exceed the budget`() {
        val allThree = ModelRegistry.ONNX_CTC.values.sumOf { it.estimatedResidentBytes }
        val a = MemoryBudget.assess(
            sttModel = null,
            ttsModelBytes = MemoryBudget.VITS_INT8_ESTIMATE_BYTES,
        ).copy(sttResidentBytes = allThree)

        assertTrue(
            !a.withinBudget,
            "expected three resident models to exceed the budget: ${a.describe()}",
        )
    }

    /**
     * The bundled-asset total is an install-size concern, not a RAM one, and it is the
     * reason distribution goes through an App Bundle with install-time asset packs
     * rather than a plain APK. Asserted so the figure cannot drift unnoticed.
     */
    @Test
    fun `bundled model assets exceed the plain apk limit`() {
        val sttTotal = ModelRegistry.ONNX_CTC.values.sumOf { it.approxSizeBytes }
        val ttsTotal = MemoryBudget.VITS_INT8_ESTIMATE_BYTES * Language.entries.size
        val bundled = sttTotal + ttsTotal

        assertTrue(
            bundled > 150L * 1024 * 1024,
            "if bundled assets now fit in 150 MB, revisit the asset-pack decision " +
                "in docs/MEASUREMENTS.md (currently ${bundled / 1024 / 1024} MB)",
        )
    }

    @Test
    fun `the assessment describes itself as an estimate`() {
        // Guards against these numbers ever being quoted as measurements.
        val text = MemoryBudget.assess(
            ModelRegistry.ONNX_CTC[Language.HINDI],
            MemoryBudget.VITS_INT8_ESTIMATE_BYTES,
        ).describe()
        assertTrue(text.contains("ESTIMATE"), "budget output must not read as a measurement: $text")
    }
}
