package crypto

import java.security.MessageDigest

object SHACrypto {
    private val EXTRA_SALT = "JoozdLog".toByteArray()

    fun hash(itemToHash: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(itemToHash)

    fun hashWithSalt(salt: ByteArray, item: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(salt + item)

    fun hashWithSalt(salt: String, item: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(salt.toByteArray() + EXTRA_SALT + item)
}