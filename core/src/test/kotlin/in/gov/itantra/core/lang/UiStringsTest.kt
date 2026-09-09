package `in`.gov.itantra.core.lang

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UiStringsTest {

    @Test
    fun `every language has a non-blank value for every key`() {
        for (language in Language.entries) {
            val strings = UiStrings.forLanguage(language)
            for ((key, value) in strings.allValues()) {
                assertTrue(value.isNotBlank(), "$language $key")
            }
        }
    }

    @Test
    fun `pairing and queue templates substitute placeholders`() {
        val s = UiStrings.forLanguage(Language.ENGLISH)
        assertTrue(s.pairingBody("123456").contains("123456"))
        assertEquals("2 queued messages — tap to open, then Play", s.queuedBody(2, "ignored"))
        assertTrue(s.queuedBody(1, "hello").contains("hello"))
        assertTrue(s.waitingToSend(3, 1).contains("3"))
    }

    @Test
    fun `every language has its own chrome, not an english fallback`() {
        // Gujarati, Marathi, Kannada, Malayalam, Telugu and Odia had no table at all,
        // so choosing one of them as the app language produced a fully English UI.
        val english = UiStrings.forLanguage(Language.ENGLISH)
        for (language in Language.entries) {
            if (language == Language.ENGLISH) continue
            val strings = UiStrings.forLanguage(language)
            for (key in TRANSLATED_KEYS) {
                val mine = strings.allValues().first { it.first == key }.second
                val theirs = english.allValues().first { it.first == key }.second
                assertNotEquals(
                    theirs,
                    mine,
                    "$language still shows the English string for $key",
                )
            }
        }
    }

    @Test
    fun `every language keeps the placeholders its templates need`() {
        for (language in Language.entries) {
            val strings = UiStrings.forLanguage(language)
            for ((key, value) in strings.allValues()) {
                val required = PLACEHOLDERS[key] ?: continue
                for (token in required) {
                    assertTrue(
                        value.contains(token),
                        "$language $key dropped the $token placeholder: \"$value\"",
                    )
                }
            }
        }
    }

    @Test
    fun `no language block was pasted in the wrong script`() {
        // Every string is written in its own script; shared Latin (iTantra, PTT, JSON)
        // and punctuation are ignored, so only a genuinely foreign Indic character
        // trips this.
        for (language in Language.entries) {
            val block = INDIC_BLOCKS[language] ?: continue
            for ((key, value) in UiStrings.forLanguage(language).allValues()) {
                for (ch in value) {
                    val c = ch.code
                    if (c < FIRST_INDIC || c > LAST_INDIC) continue
                    if (c in SHARED_PUNCTUATION) continue
                    assertTrue(
                        c in block,
                        "$language $key contains U+%04X, outside its script block".format(c),
                    )
                }
            }
        }
    }

    private companion object {
        /**
         * Keys every language must actually translate. Deliberately excludes strings
         * that are the same everywhere -- the app name, PTT, and the like.
         */
        val TRANSLATED_KEYS = listOf(
            "talk", "alert", "analysis", "settings", "connect", "disconnect",
            "save", "back", "cancel", "profile", "history", "radar", "theme",
        )

        val PLACEHOLDERS: Map<String, List<String>> = mapOf(
            "packsPlaceholderTemplate" to listOf("{languages}"),
            "pairingBodyTemplate" to listOf("{code}"),
            "languageLineTemplate" to listOf("{endonym}", "{english}"),
            "waitingToSendTemplate" to listOf("{n}"),
            "waitingToSendFailedTemplate" to listOf("{n}", "{failed}"),
            "connectionFailedTemplate" to listOf("{detail}"),
            "pairingFailedTemplate" to listOf("{detail}"),
            "playbackErrorTemplate" to listOf("{detail}"),
            "queueSendFailedTemplate" to listOf("{detail}"),
            "errorTemplate" to listOf("{detail}"),
            "queuedManyTemplate" to listOf("{count}"),
            "receivedFromTemplate" to listOf("{name}"),
            "sentToTemplate" to listOf("{name}"),
            "outboxTemplate" to listOf("{pending}", "{failed}"),
            "alertPrefixTemplate" to listOf("{text}"),
            "discoveredTemplate" to listOf("{count}"),
            "connectToPeerTemplate" to listOf("{name}"),
        )

        const val FIRST_INDIC = 0x0900
        const val LAST_INDIC = 0x0D7F

        /**
         * The danda and double danda live in the Devanagari block but are shared
         * sentence punctuation across the northern scripts -- Bengali and Odia use
         * them too, so they are not evidence of a wrong-script paste.
         */
        val SHARED_PUNCTUATION = setOf(0x0964, 0x0965)

        val INDIC_BLOCKS: Map<Language, IntRange> = mapOf(
            Language.HINDI to 0x0900..0x097F,
            Language.MARATHI to 0x0900..0x097F,
            Language.BENGALI to 0x0980..0x09FF,
            Language.GUJARATI to 0x0A80..0x0AFF,
            Language.ODIA to 0x0B00..0x0B7F,
            Language.TAMIL to 0x0B80..0x0BFF,
            Language.TELUGU to 0x0C00..0x0C7F,
            Language.KANNADA to 0x0C80..0x0CFF,
            Language.MALAYALAM to 0x0D00..0x0D7F,
        )
    }

    @Test
    fun `error and empty queue bodies never go blank`() {
        val s = UiStrings.forLanguage(Language.HINDI)
        assertTrue(s.genericError(null).isNotBlank())
        assertTrue(s.genericError("boom").contains("boom"))
        assertEquals("", s.queuedBody(0, "ignored"))
        assertEquals(s.queuedOneFallback, s.queuedBody(1, "   "))
    }
}
