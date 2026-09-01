package `in`.gov.itantra.core.crypto

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Raised when a GCM tag fails to verify. Never retry; discard and count. */
class AuthenticationFailedException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Ciphertext (with the GCM tag appended) plus the nonce it was produced under. */
class SealedPayload(val nonce: ByteArray, val ciphertext: ByteArray)

/**
 * Authenticated encryption for one transport session.
 *
 * Kept as an interface so the packet codec and its tests are independent of the key
 * source: production derives the key through Android Keystore ECDH, tests inject a
 * fixed key.
 */
interface SessionCrypto {
    fun seal(plaintext: ByteArray, associatedData: ByteArray): SealedPayload

    /** @throws AuthenticationFailedException if the tag or associated data do not match. */
    fun open(ciphertext: ByteArray, nonce: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a per-session key and a never-repeating nonce.
 *
 * Nonce construction is the part worth reading. GCM fails catastrophically if a
 * (key, nonce) pair is ever reused -- not "slightly weaker", but full loss of
 * confidentiality for both messages and recovery of the authentication subkey. A
 * random 96-bit nonce would be acceptable in isolation, but this app runs on cheap
 * hardware where entropy at boot is not something to depend on blindly.
 *
 * So the nonce is 4 random bytes fixed for the life of the session, followed by a
 * 64-bit counter. The random prefix keeps two sessions that somehow share a key from
 * colliding; the counter guarantees uniqueness within the session without relying on
 * the RNG at all. The counter is checked for overflow and the session is retired well
 * before wraparound.
 */
class AesGcmSessionCrypto(
    key: ByteArray,
    private val random: SecureRandom = SecureRandom(),
) : SessionCrypto {

    init {
        require(key.size == KEY_LEN) {
            "AES-256-GCM needs a $KEY_LEN-byte key, got ${key.size}"
        }
    }

    private val secretKey: SecretKey = SecretKeySpec(key, "AES")
    private val noncePrefix = ByteArray(NONCE_PREFIX_LEN).also { random.nextBytes(it) }
    private val counter = AtomicLong(0)

    /** Messages sealed so far. Module B7 reads this; also the rekey trigger. */
    val sealedCount: Long get() = counter.get()

    override fun seal(plaintext: ByteArray, associatedData: ByteArray): SealedPayload {
        val n = counter.getAndIncrement()
        if (n >= MAX_MESSAGES_PER_SESSION) {
            throw IllegalStateException(
                "session nonce space exhausted after $n messages; renegotiate the session key"
            )
        }
        val nonce = buildNonce(n)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return SealedPayload(nonce, cipher.doFinal(plaintext))
    }

    override fun open(ciphertext: ByteArray, nonce: ByteArray, associatedData: ByteArray): ByteArray {
        require(nonce.size == NONCE_LEN) { "nonce must be $NONCE_LEN bytes, got ${nonce.size}" }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw AuthenticationFailedException("GCM tag verification failed", e)
        } catch (e: javax.crypto.BadPaddingException) {
            // Some providers surface tag failure as the parent type rather than AEADBadTagException.
            throw AuthenticationFailedException("GCM tag verification failed", e)
        }
    }

    private fun buildNonce(counterValue: Long): ByteArray {
        val nonce = ByteArray(NONCE_LEN)
        System.arraycopy(noncePrefix, 0, nonce, 0, NONCE_PREFIX_LEN)
        for (i in 0 until 8) {
            nonce[NONCE_PREFIX_LEN + i] = (counterValue ushr (56 - 8 * i)).toByte()
        }
        return nonce
    }

    companion object {
        const val KEY_LEN = 32
        const val NONCE_LEN = 12
        const val TAG_BITS = 128

        private const val NONCE_PREFIX_LEN = 4
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /**
         * Far below the 2^32 NIST guidance for a fixed key, and far above anything a
         * field session will send. Crossing it is a bug, not a normal condition.
         */
        const val MAX_MESSAGES_PER_SESSION = 1L shl 40
    }
}
