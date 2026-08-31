package `in`.gov.itantra.core.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * One side's ephemeral ECDH key pair for a pairing handshake.
 *
 * [publicKeyEncoded] is the X.509 SubjectPublicKeyInfo encoding, which is what gets
 * sent over the wire during connection setup.
 */
interface EphemeralKeyPair : AutoCloseable {
    val publicKeyEncoded: ByteArray

    /**
     * Completes ECDH against the peer's encoded public key and returns the raw shared
     * secret (the X coordinate). This value must never be used as a key directly --
     * it is not uniformly distributed. Pass it through [SessionKeyDerivation].
     */
    fun computeSharedSecret(peerPublicKeyEncoded: ByteArray): ByteArray
}

/**
 * Creates ephemeral P-256 key pairs.
 *
 * The Android implementation backs these with the hardware-isolated Keystore where
 * available; the JVM implementation exists so the handshake, key derivation and
 * pairing-code logic can be tested without a device.
 */
interface KeyAgreementProvider {
    fun generateEphemeralKeyPair(): EphemeralKeyPair

    /** True when private keys are held in hardware and cannot be extracted. */
    val isHardwareBacked: Boolean
}

/**
 * HKDF-SHA256 (RFC 5869) used to turn a raw ECDH secret into an AES-256 session key.
 *
 * A plain SHA-256 of the shared secret would be the tempting shortcut and is a real
 * weakness: the raw ECDH output has structure and biased bits, and hashing alone
 * provides no domain separation between this protocol and any other use of the same
 * curve. HKDF extract-then-expand handles both, and the [info] string pins the key to
 * this application and protocol version.
 */
object SessionKeyDerivation {

    private const val HMAC = "HmacSHA256"
    private const val HASH_LEN = 32

    /** Domain separation. Change this if the handshake ever changes shape. */
    const val INFO = "iTantra/v1/session-key/aes-256-gcm"

    /**
     * Derives a session key from [sharedSecret].
     *
     * [salt] should be a transcript of the handshake -- both public keys in a
     * canonical order -- so that the derived key is bound to the exact exchange that
     * produced it and cannot be transplanted into another session.
     */
    fun deriveKey(sharedSecret: ByteArray, salt: ByteArray, length: Int = 32): ByteArray {
        val prk = extract(salt, sharedSecret)
        return expand(prk, INFO.toByteArray(Charsets.UTF_8), length)
    }

    /**
     * Canonical handshake transcript: the two encoded public keys sorted by their
     * byte content. Sorting makes it order-independent, so both peers derive the same
     * salt without needing to agree on who is "initiator".
     */
    fun transcript(publicKeyA: ByteArray, publicKeyB: ByteArray): ByteArray {
        val (first, second) = orderKeys(publicKeyA, publicKeyB)
        return first + second
    }

    internal fun orderKeys(a: ByteArray, b: ByteArray): Pair<ByteArray, ByteArray> =
        if (compareBytes(a, b) <= 0) a to b else b to a

    internal fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(effectiveSalt, HMAC))
        return mac.doFinal(ikm)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * HASH_LEN) { "HKDF cannot expand to $length bytes" }
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(prk, HMAC))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }
}

/**
 * The 6-digit short authentication string shown to both operators during pairing.
 *
 * This is the only defence against an active man-in-the-middle. Unauthenticated ECDH
 * is perfectly happy to negotiate two separate sessions with an attacker sitting in
 * the middle; what breaks that is the two humans confirming that both handsets display
 * the same six digits, which they cannot if the attacker holds different keys with
 * each side.
 *
 * The code is derived from the *shared secret* as well as the transcript. Deriving it
 * from the public keys alone would let an attacker who can relay keys reproduce the
 * expected digits. Because the attacker cannot know either shared secret, the codes
 * diverge and the humans see a mismatch.
 *
 * Displaying and confirming the code is a UI concern and out of scope for this pass;
 * this class only computes and exposes it.
 */
object PairingCode {

    private const val DIGITS = 6
    private const val MODULUS = 1_000_000

    /** Domain-separated so the pairing code can never collide with the session key. */
    private const val INFO = "iTantra/v1/pairing-sas"

    /**
     * Computes the value both peers must display. Order-independent: the same pair of
     * public keys yields the same code regardless of which side calls it.
     *
     * @return the code as an exactly 6-character zero-padded decimal string.
     */
    fun derive(sharedSecret: ByteArray, publicKeyA: ByteArray, publicKeyB: ByteArray): String {
        val transcript = SessionKeyDerivation.transcript(publicKeyA, publicKeyB)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
        mac.update(INFO.toByteArray(Charsets.UTF_8))
        mac.update(transcript)
        val digest = mac.doFinal()

        // Big-endian truncation of the leading 4 bytes, sign bit cleared.
        val value = ((digest[0].toInt() and 0x7F) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)

        return (value % MODULUS).toString().padStart(DIGITS, '0')
    }

    /** Constant-time comparison, for when a future UI confirms a typed-in code. */
    fun matches(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
}
