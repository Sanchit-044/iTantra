package `in`.gov.itantra.core.crypto

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Exercises the handshake maths on the JVM. The Android implementation swaps the key
 * source for Android Keystore but performs the identical derivation, so everything
 * verified here holds there too.
 */
class KeyAgreementTest {

    private class Party {
        val kp = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        val publicKeyEncoded: ByteArray get() = kp.public.encoded

        fun sharedSecretWith(peer: ByteArray): ByteArray {
            val kf = java.security.KeyFactory.getInstance("EC")
            val peerKey = kf.generatePublic(java.security.spec.X509EncodedKeySpec(peer))
            return KeyAgreement.getInstance("ECDH").run {
                init(kp.private)
                doPhase(peerKey, true)
                generateSecret()
            }
        }
    }

    @Test
    fun `both sides derive the same p256 shared secret`() {
        val a = Party()
        val b = Party()
        assertContentEquals(
            a.sharedSecretWith(b.publicKeyEncoded),
            b.sharedSecretWith(a.publicKeyEncoded),
        )
    }

    @Test
    fun `both sides derive the same session key`() {
        val a = Party()
        val b = Party()
        val salt = SessionKeyDerivation.transcript(a.publicKeyEncoded, b.publicKeyEncoded)

        val ka = SessionKeyDerivation.deriveKey(a.sharedSecretWith(b.publicKeyEncoded), salt)
        val kb = SessionKeyDerivation.deriveKey(b.sharedSecretWith(a.publicKeyEncoded), salt)

        assertContentEquals(ka, kb)
        assertEquals(32, ka.size, "session key must be 32 bytes for AES-256")
    }

    @Test
    fun `the transcript is order independent`() {
        val a = Party().publicKeyEncoded
        val b = Party().publicKeyEncoded
        assertContentEquals(
            SessionKeyDerivation.transcript(a, b),
            SessionKeyDerivation.transcript(b, a),
        )
    }

    @Test
    fun `the derived key is not merely a hash of the shared secret`() {
        val a = Party()
        val b = Party()
        val secret = a.sharedSecretWith(b.publicKeyEncoded)
        val key = SessionKeyDerivation.deriveKey(secret, ByteArray(0))
        val plainSha = java.security.MessageDigest.getInstance("SHA-256").digest(secret)
        assertFalse(key.contentEquals(plainSha), "HKDF collapsed to a plain hash")
    }

    @Test
    fun `a different transcript yields a different key`() {
        val a = Party()
        val b = Party()
        val secret = a.sharedSecretWith(b.publicKeyEncoded)
        val k1 = SessionKeyDerivation.deriveKey(secret, "salt-one".toByteArray())
        val k2 = SessionKeyDerivation.deriveKey(secret, "salt-two".toByteArray())
        assertFalse(k1.contentEquals(k2), "key is not bound to the handshake transcript")
    }
}

class PairingCodeTest {

    private fun secret(seed: Byte) = ByteArray(32) { seed }
    private val pubA = ByteArray(64) { 1 }
    private val pubB = ByteArray(64) { 2 }

    @Test
    fun `is exactly six digits`() {
        val code = PairingCode.derive(secret(9), pubA, pubB)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() }, "pairing code contained a non-digit: $code")
    }

    @Test
    fun `both peers compute the same code regardless of argument order`() {
        assertEquals(
            PairingCode.derive(secret(9), pubA, pubB),
            PairingCode.derive(secret(9), pubB, pubA),
        )
    }

    /**
     * The property that defeats a man-in-the-middle. The attacker negotiates a
     * different shared secret with each side, so the two handsets display different
     * codes and the operators see the mismatch.
     */
    @Test
    fun `a different shared secret yields a different code`() {
        assertNotEquals(
            PairingCode.derive(secret(1), pubA, pubB),
            PairingCode.derive(secret(2), pubA, pubB),
        )
    }

    @Test
    fun `a different peer key yields a different code`() {
        assertNotEquals(
            PairingCode.derive(secret(9), pubA, pubB),
            PairingCode.derive(secret(9), pubA, ByteArray(64) { 3 }),
        )
    }

    @Test
    fun `small codes keep their leading zeros`() {
        // Scan for a code that is numerically small; it must still render as 6 chars.
        for (i in 0..500) {
            val c = PairingCode.derive(secret(i.toByte()), pubA, ByteArray(64) { i.toByte() })
            assertEquals(6, c.length, "lost zero padding on code $c")
        }
    }

    @Test
    fun `matches compares correctly`() {
        assertTrue(PairingCode.matches("012345", "012345"))
        assertFalse(PairingCode.matches("012345", "012346"))
    }
}

class AesGcmSessionCryptoTest {

    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun `seals and opens`() {
        val c = AesGcmSessionCrypto(key)
        val aad = "header".toByteArray()
        val sealed = c.seal("secret message".toByteArray(), aad)
        assertEquals("secret message", String(c.open(sealed.ciphertext, sealed.nonce, aad)))
    }

    @Test
    fun `open fails when the associated data differs`() {
        val c = AesGcmSessionCrypto(key)
        val sealed = c.seal("m".toByteArray(), "header-A".toByteArray())
        assertFailsWith<AuthenticationFailedException> {
            c.open(sealed.ciphertext, sealed.nonce, "header-B".toByteArray())
        }
    }

    @Test
    fun `ciphertext carries the 16 byte gcm tag`() {
        val c = AesGcmSessionCrypto(key)
        val plaintext = "1234567890".toByteArray()
        val sealed = c.seal(plaintext, ByteArray(0))
        assertEquals(
            plaintext.size + 16, sealed.ciphertext.size,
            "expected a 128-bit GCM tag appended to the ciphertext",
        )
    }

    @Test
    fun `rejects a key of the wrong length`() {
        assertFailsWith<IllegalArgumentException> { AesGcmSessionCrypto(ByteArray(16)) }
    }

    @Test
    fun `identical plaintexts produce different ciphertexts`() {
        val c = AesGcmSessionCrypto(key)
        val a = c.seal("same".toByteArray(), ByteArray(0))
        val b = c.seal("same".toByteArray(), ByteArray(0))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext), "deterministic ciphertext leaks equality")
    }
}
