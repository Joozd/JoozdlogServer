package data.email

import nl.joozd.serializing.JoozdSerializable
import nl.joozd.serializing.unwrap
import nl.joozd.serializing.wrap

/**
 * This will be used to represent hashed data for checking if an email address has been confirmed.
 * The username is the filename
 * @param salt: unique salt for this user
 * @param hash: Hash of user's email + salt
 * @param confirmed: user has confirmed his email address
 */

@Suppress("ArrayInDataClass") // don't compare this
data class EmailHashData(val salt: ByteArray, val hash: ByteArray, val confirmed: Boolean): JoozdSerializable {
    override fun serialize(): ByteArray {
        var serialized = ByteArray(0)
        serialized += wrap(component1())
        serialized += wrap(component2())
        serialized += wrap(component3())
        return serialized
    }

    companion object : JoozdSerializable.Deserializer<EmailHashData> {

        override fun deserialize(source: ByteArray): EmailHashData {
            val wraps = serializedToWraps(source)
            return EmailHashData(
                unwrap(wraps[0]),
                unwrap(wraps[1]),
                unwrap(wraps[2])
            )
        }
    }
}