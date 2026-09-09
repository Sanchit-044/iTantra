package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlertCodecTest {

    @Test
    fun testBleEncodingDecoding_Template() {
        val originalLanguage = Language.TAMIL
        val originalContent = AlertContent.Template(AlertTemplate.MEDICAL_HELP)
        val originalSequence = 42L

        val encoded = AlertCodec.encodeBlePayload(originalLanguage, originalContent, originalSequence)
        assertNotNull(encoded, "Encoded payload should not be null for templates")
        assertEquals(3, encoded.size, "Encoded BLE payload should be exactly 3 bytes")

        val decoded = AlertCodec.decodeBlePayload(encoded)
        assertNotNull(decoded, "Decoded alert should not be null")
        assertEquals(originalLanguage, decoded.language)
        assertEquals(originalContent, decoded.content)
        assertEquals(originalSequence.toInt(), decoded.sequence)
    }

    @Test
    fun testBleEncoding_CustomAlert_Fails() {
        val content = AlertContent.Custom("This is a test alert that is too long for BLE")
        val encoded = AlertCodec.encodeBlePayload(Language.ENGLISH, content, 1L)
        assertNull(encoded, "Custom alerts should return null since they do not fit in BLE")
    }

    @Test
    fun testWifiEncodingDecoding_Template() {
        val originalLanguage = Language.ODIA
        val originalContent = AlertContent.Template(AlertTemplate.EVACUATE_IMMEDIATELY)
        val originalSequence = 99L

        val encoded = AlertCodec.encodeWifiPayload(originalLanguage, originalContent, originalSequence)
        assertEquals("or", encoded["lang"])
        assertEquals("99", encoded["seq"])
        assertEquals("tpl:evacuate", encoded["payload"])

        val decoded = AlertCodec.decodeWifiPayload(encoded)
        assertNotNull(decoded, "Decoded Wi-Fi alert should not be null")
        assertEquals(originalLanguage, decoded.language)
        assertEquals(originalContent, decoded.content)
        assertEquals(originalSequence.toInt(), decoded.sequence)
    }

    @Test
    fun testWifiEncodingDecoding_Custom() {
        val originalLanguage = Language.BENGALI
        val originalContent = AlertContent.Custom("This is a custom string alert. It can be long!")
        val originalSequence = 123L

        val encoded = AlertCodec.encodeWifiPayload(originalLanguage, originalContent, originalSequence)
        assertEquals("bn", encoded["lang"])
        assertEquals("123", encoded["seq"])
        assertEquals("This is a custom string alert. It can be long!", encoded["payload"])

        val decoded = AlertCodec.decodeWifiPayload(encoded)
        assertNotNull(decoded)
        assertEquals(originalLanguage, decoded.language)
        assertEquals(originalContent, decoded.content)
        assertEquals(originalSequence.toInt(), decoded.sequence)
    }
}
