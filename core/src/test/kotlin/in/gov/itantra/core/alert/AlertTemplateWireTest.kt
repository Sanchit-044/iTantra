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
    fun `plain text is treated as a custom alert not a template`() {
        val content = AlertTemplate.fromWirePayload("तुरंत निकलें")
        assertEquals(AlertContent.Custom("तुरंत निकलें"), content)
    }
}
