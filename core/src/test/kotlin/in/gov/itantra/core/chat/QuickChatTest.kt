package `in`.gov.itantra.core.chat

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertTrue

class QuickChatTest {

    @Test
    fun `every language has a non-blank phrase for every quick chat`() {
        for (chat in QuickChat.entries) {
            for (language in Language.entries) {
                val phrase = chat.phrase(language)
                assertTrue(phrase.isNotBlank(), "$chat has no phrase for $language")
            }
        }
    }

    @Test
    fun `marathi is not an accidental English fallback`() {
        // Regression test: quick chats were originally built on UiStrings, whose
        // catalog only covers English/Hindi/Tamil/Bengali -- Marathi (and five other
        // languages) silently fell back to English text tagged as the wrong
        // language. QuickChat exists specifically so this can't happen again.
        for (chat in QuickChat.entries) {
            assertTrue(
                chat.phrase(Language.MARATHI) != chat.phrase(Language.ENGLISH),
                "$chat: Marathi phrase must not equal the English one",
            )
        }
    }
}
