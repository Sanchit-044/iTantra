package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertTemplateWireTest {

    @Test
    fun `every language has a non-blank phrase`() {
        for (template in AlertTemplate.entries) {
            for (language in Language.entries) {
                assertTrue(template.phrase(language).isNotBlank(), "$template $language")
            }
        }
    }

    @Test
    fun `wire payload round-trips all five templates`() {
        for (template in AlertTemplate.entries) {
            val encoded = AlertContent.Template(template).toWirePayload()
            assertEquals(AlertContent.Template(template), AlertTemplate.fromWirePayload(encoded))
        }
    }

    @Test
    fun `resolveDisplayText resolves wire prefix to localized phrase`() {
        assertEquals("आपातकाल — सहायता चाहिए", AlertTemplate.resolveDisplayText("tpl:emergency", Language.HINDI))
        assertEquals("Emergency — Assistance needed", AlertTemplate.resolveDisplayText("tpl:emergency", Language.ENGLISH))
        assertEquals("કટોકટી — મદદ જોઈએ છે", AlertTemplate.resolveDisplayText("tpl:emergency", Language.GUJARATI))
        assertEquals("Custom plain text", AlertTemplate.resolveDisplayText("Custom plain text", Language.HINDI))
    }
}
