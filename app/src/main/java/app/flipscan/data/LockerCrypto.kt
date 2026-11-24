package app.flipscan.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encryption for Digital Locker records.
 *
 * The key is generated once inside Android Keystore under [ALIAS] and never leaves it: a hardware
 * backed key on phones that provide one. Every record is sealed separately with AES-256-GCM and a
 * fresh IV from the Keystore, so identical pages never produce identical ciphertext. The GCM tag
 * makes a modified or truncated file fail to decrypt instead of opening as garbage.
 *
 * Envelope: "FSLK1" | iv length (1 byte) | iv | ciphertext + 16-byte tag.
 *
 * The key is intentionally not bound to biometric authentication: the locker is gated by
 * BiometricPrompt at the app level, while the key must stay usable for background work such as
 * re-encrypting a crop. Binding it would also make every biometric enrolment change destroy the
 * stored documents.
 */
object LockerCrypto {
    private const val ALIAS = "flipscan_locker_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val MAGIC = "FSLK1"
    private const val MAGIC_LEN = 5
    private const val TAG_BITS = 128

    private val lock = Any()

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        val existing = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing
        synchronized(lock) {
            // Another thread may have created it while this one waited.
            val again = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            if (again != null) return again
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // The Keystore generates a fresh random IV for every init(ENCRYPT_MODE).
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        }
    }

    /** Seals [plain] into a self-describing envelope. Blocking: call off the main thread. */
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        if (iv.size > 255) throw GeneralSecurityException("Unsupported IV")
        val body = cipher.doFinal(plain)
        return ByteArrayOutputStream(MAGIC_LEN + 1 + iv.size + body.size).use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(iv.size)
            out.write(iv)
            out.write(body)
            out.toByteArray()
        }
    }

    /** Opens an envelope produced by [encrypt]. Throws if the key or the bytes are not valid. */
    fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size < MAGIC_LEN + 1 + 12 + TAG_BITS / 8) {
            throw GeneralSecurityException("Record is too short")
        }
        val magic = String(blob, 0, MAGIC_LEN, Charsets.US_ASCII)
        if (magic != MAGIC) throw GeneralSecurityException("Not a locker record")
        val ivLen = blob[MAGIC_LEN].toInt() and 0xFF
        val ivStart = MAGIC_LEN + 1
        val bodyStart = ivStart + ivLen
        if (ivLen < 12 || bodyStart >= blob.size) throw GeneralSecurityException("Bad record header")
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, blob, ivStart, ivLen))
        return cipher.doFinal(blob, bodyStart, blob.size - bodyStart)
    }

    fun encryptText(text: String): ByteArray = encrypt(text.toByteArray(Charsets.UTF_8))

    fun decryptText(blob: ByteArray): String = String(decrypt(blob), Charsets.UTF_8)
}
