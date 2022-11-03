package data.email

import crypto.SHACrypto
import nl.joozd.joozdlogcommon.EmailData
import utils.base64EncodeLinkSafe
import java.time.Instant

data class EmailDataRecord(val id: Long, val hash: ByteArray, val salt: ByteArray, val lastAccessed: Long, val isVerified: Boolean) {
    constructor(dao: EmailDataDao): this(dao.id.value, dao.hash, dao.salt, dao.lastAccessed, dao.isVerified)

    infix fun matches(emailAddress: String) =
        SHACrypto.hash(emailAddress, salt).contentEquals(hash)

    fun confirmationString(): String {
        val hashedEmailBase64: String = base64EncodeLinkSafe(hash)
        return "$id:$hashedEmailBase64"
    }

    override fun toString() = "emailData: userName=$id, hash=${hash.toString(Charsets.UTF_8)}, salt=${salt.toString(Charsets.UTF_8)}, lastAccessed = ${Instant.ofEpochSecond(lastAccessed)}, isVerified=$isVerified"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmailDataRecord) return false

        return id == other.id
                && hash.contentEquals(other.hash)
                && salt.contentEquals(other.salt)
                && lastAccessed == other.lastAccessed
                && isVerified == other.isVerified
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + hash.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + lastAccessed.hashCode()
        result = 31 * result + isVerified.hashCode()
        return result
    }
    companion object{
        fun generate(emailAddress: String): EmailDataRecord {
            val salt = SHACrypto.generateRandomSalt()
            val hash = SHACrypto.hash(emailAddress, salt)
            return EmailDataRecord(
                id = EmailData.EMAIL_ID_NOT_SET,
                hash = hash,
                salt = salt,
                lastAccessed = Instant.now().epochSecond,
                isVerified = false
            )
        }
    }
}