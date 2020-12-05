package crypto

import java.security.MessageDigest

object SHACrypto {
    private val EXTRA_SALT = "JoozdLog".toByteArray()

    fun hash(itemToHash: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(itemToHash)

    fun hashWithSalt(salt: ByteArray, item: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(salt + item)

    fun hashWithSalt(salt: ByteArray, item: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(salt + item.toByteArray(Charsets.UTF_8))

    /**
     * Adds a bit of extra salt so rainbow table of known hash of password with username as salt won't work
     */
    fun hashWithExtraSalt(salt: String, item: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(salt.toByteArray() + EXTRA_SALT + item)
}