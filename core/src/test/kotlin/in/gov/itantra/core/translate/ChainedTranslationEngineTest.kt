package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language
import org.junit.Assert.*
import org.junit.Test

class ChainedTranslationEngineTest {

    private class StubEngine(
        override val isAvailable: Boolean,
        private val result: String? = null,
        private val runtimeCrash: Boolean = false,
    ) : TranslationEngine {
        var called = false
            private set

        override fun translate(text: String, source: Language, target: Language): String {
            called = true
            if (runtimeCrash) throw RuntimeException("ONNX native crash simulation")
            return result
                ?: throw TranslationUnavailableException("StubEngine: not available")
        }
    }

    @Test
    fun `same language returns text unchanged without calling any delegate`() {
        val engine = StubEngine(isAvailable = true, result = "translated")
        val chained = ChainedTranslationEngine(listOf(engine))

        val result = chained.translate("hello", Language.HINDI, Language.HINDI)

        assertEquals("hello", result)
        assertFalse(engine.called)
    }

    @Test
    fun `first available engine wins`() {
        val first = StubEngine(isAvailable = true, result = "from-first")
        val second = StubEngine(isAvailable = true, result = "from-second")
        val chained = ChainedTranslationEngine(listOf(first, second))

        val result = chained.translate("text", Language.HINDI, Language.BENGALI)

        assertEquals("from-first", result)
        assertTrue(first.called)
        assertFalse("Second engine should not be called", second.called)
    }

    @Test
    fun `falls back to second engine when first throws`() {
        val first = StubEngine(isAvailable = true, result = null)
        val second = StubEngine(isAvailable = true, result = "from-fallback")
        val chained = ChainedTranslationEngine(listOf(first, second))

        val result = chained.translate("text", Language.HINDI, Language.BENGALI)

        assertEquals("from-fallback", result)
        assertTrue(first.called)
        assertTrue(second.called)
    }

    @Test
    fun `skips unavailable engines`() {
        val unavailable = StubEngine(isAvailable = false, result = "nope")
        val available = StubEngine(isAvailable = true, result = "yes")
        val chained = ChainedTranslationEngine(listOf(unavailable, available))

        val result = chained.translate("text", Language.HINDI, Language.TAMIL)

        assertEquals("yes", result)
        assertFalse("Unavailable engine should be skipped", unavailable.called)
        assertTrue(available.called)
    }

    @Test(expected = TranslationUnavailableException::class)
    fun `throws when all engines fail`() {
        val first = StubEngine(isAvailable = true, result = null)
        val second = StubEngine(isAvailable = true, result = null)
        val chained = ChainedTranslationEngine(listOf(first, second))

        chained.translate("text", Language.HINDI, Language.BENGALI)
    }

    @Test(expected = TranslationUnavailableException::class)
    fun `throws when all engines are unavailable`() {
        val chained = ChainedTranslationEngine(
            listOf(
                StubEngine(isAvailable = false),
                StubEngine(isAvailable = false),
            )
        )
        chained.translate("text", Language.HINDI, Language.BENGALI)
    }

    @Test
    fun `isAvailable is true if any delegate is available`() {
        val chained = ChainedTranslationEngine(
            listOf(
                StubEngine(isAvailable = false),
                StubEngine(isAvailable = true),
            )
        )
        assertTrue(chained.isAvailable)
    }

    @Test
    fun `isAvailable is false when all delegates are unavailable`() {
        val chained = ChainedTranslationEngine(
            listOf(
                StubEngine(isAvailable = false),
                StubEngine(isAvailable = false),
            )
        )
        assertFalse(chained.isAvailable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty delegate list`() {
        ChainedTranslationEngine(emptyList())
    }

    @Test
    fun `dictionary plus null-onnx chain works for known phrases`() {
        val dictionary = DictionaryTranslationEngine()
        val onnxStub = StubEngine(isAvailable = false)
        val chained = ChainedTranslationEngine(listOf(dictionary, onnxStub))

        val result = chained.translate(
            "मुझे मदद चाहिए",
            Language.HINDI,
            Language.BENGALI,
        )
        assertEquals("আমার সাহায্য দরকার", result)
        assertFalse("ONNX stub should not be called", onnxStub.called)
    }

    @Test
    fun `falls back when first engine throws RuntimeException`() {
        val crashingEngine = StubEngine(isAvailable = true, runtimeCrash = true)
        val fallback = StubEngine(isAvailable = true, result = "recovered")
        val chained = ChainedTranslationEngine(listOf(crashingEngine, fallback))

        val result = chained.translate("text", Language.HINDI, Language.BENGALI)

        assertEquals("recovered", result)
        assertTrue(crashingEngine.called)
        assertTrue(fallback.called)
    }

    @Test(expected = TranslationUnavailableException::class)
    fun `wraps RuntimeException as TranslationUnavailableException when all fail`() {
        val crashingEngine = StubEngine(isAvailable = true, runtimeCrash = true)
        val chained = ChainedTranslationEngine(listOf(crashingEngine))

        chained.translate("text", Language.HINDI, Language.BENGALI)
    }

    @Test
    fun `exception message includes details when RuntimeException wraps`() {
        val crashingEngine = StubEngine(isAvailable = true, runtimeCrash = true)
        val chained = ChainedTranslationEngine(listOf(crashingEngine))

        try {
            chained.translate("text", Language.HINDI, Language.BENGALI)
            fail("Expected TranslationUnavailableException")
        } catch (e: TranslationUnavailableException) {
            assertTrue(
                "Message should mention ONNX crash: ${e.message}",
                e.message!!.contains("ONNX native crash simulation"),
            )
        }
    }
}
