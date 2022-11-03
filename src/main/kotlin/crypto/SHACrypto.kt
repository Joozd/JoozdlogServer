package crypto

import java.security.MessageDigest
import java.security.SecureRandom

object SHACrypto {
    fun hash(item: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(salt + item.toByteArray(Charsets.UTF_8))

    fun generateRandomSalt() = ByteArray(16).apply{ SecureRandom().nextBytes(this) }
}