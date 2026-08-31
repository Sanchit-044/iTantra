package `in`.gov.itantra.android.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import `in`.gov.itantra.core.crypto.EphemeralKeyPair
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * ECDH P-256 key agreement, hardware-backed where the device allows it.
 *
 * Android Keystore gained ECDH agreement support (PURPOSE_AGREE_KEY) only in API 31.
 * Below that, Keystore can hold an EC key but cannot perform the agreement, so this
 * class falls back to an in-process key pair.
 *
 * That fallback is a genuine, reportable reduction in security, not an implementation
 * detail: an in-process private key is readable by anything that achieves code
 * execution in the app, whereas a hardware-backed key is not. [isHardwareBacked] is
 * surfaced through PairingInfo so a future UI can tell the operator which guarantee
 * they actually have, and the diagnostics module reports it. It is deliberately not
 * silent.
 */
class KeystoreKeyAgreement(
    private val keyAlias: String = DEFAULT_ALIAS,
) : KeyAgreementProvider {

    override val isHardwareBacked: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun generateEphemeralKeyPair(): EphemeralKeyPair =
        if (isHardwareBacked) KeystorePair(keyAlias) else InProcessPair()

    /** API 31+: the private key never leaves the secure element. */
    private class KeystorePair(private val alias: String) : EphemeralKeyPair {

        // Declared before keyPair: property initialisers run in declaration order, so
        // the generator below can safely read this.
        private val aliasInUse: String = "$alias-${System.nanoTime()}"

        private val keyPair: KeyPair = run {
            // A fresh key per session. Ephemeral keys give forward secrecy: a device
            // seized later cannot decrypt traffic captured earlier.
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE,
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(aliasInUse, KeyProperties.PURPOSE_AGREE_KEY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                    .build()
            )
            generator.generateKeyPair()
        }

        override val publicKeyEncoded: ByteArray get() = keyPair.public.encoded

        override fun computeSharedSecret(peerPublicKeyEncoded: ByteArray): ByteArray {
            val peer = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(peerPublicKeyEncoded))
            return KeyAgreement.getInstance("ECDH", ANDROID_KEYSTORE).run {
                init(keyPair.private)
                doPhase(peer, true)
                generateSecret()
            }
        }

        override fun close() {
            // Ephemeral: remove the entry so it cannot be reused after the session.
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE)
                    .apply { load(null) }
                    .deleteEntry(aliasInUse)
            }
        }
    }

    /** Pre-API-31 fallback. Private key lives in process memory. */
    private class InProcessPair : EphemeralKeyPair {

        private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVE))
        }.generateKeyPair()

        override val publicKeyEncoded: ByteArray get() = keyPair.public.encoded

        override fun computeSharedSecret(peerPublicKeyEncoded: ByteArray): ByteArray {
            val peer = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(peerPublicKeyEncoded))
            return KeyAgreement.getInstance("ECDH").run {
                init(keyPair.private)
                doPhase(peer, true)
                generateSecret()
            }
        }

        override fun close() = Unit
    }

    companion object {
        const val DEFAULT_ALIAS = "itantra-session"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CURVE = "secp256r1"
    }
}
