package data.email

import crypto.SHACrypto
import utils.Logger
import java.io.File
import java.io.FileNotFoundException

/**
 * This will represent the email hash data for a user
 * Use this class to:
 * [verifyEmail] against the hash,
 * [create] a new salt/hash combo
 * [confirm] that the salt/hash combo has been confirmed by the user
 */
class EmailHashFile(private val file: File) {
    constructor(fileName: String): this(File(fileName))
    val username = file.name

    var hashData: EmailHashData? = try { EmailHashData.deserialize(file.readBytes()) } catch(e: FileNotFoundException) { null }
    private set(it){
        require (it != null) { "Do not set hashData to null (error EmailHashFile001)"}
        field = it
        file.writeBytes(it.serialize())
    }

    /**
     * Verify if an email matches it's confirmed hash
     */
    fun verifyEmail(email: String): Boolean{
        return hashData?.let{
            SHACrypto.hash(email, it.salt).contentEquals(it.hash) && it.confirmed
        } ?: false
    }


    /**
     * This will only create the data, not send the email
     * @param email: The email to hash
     * @return the data just created
     */
    fun create(email: String): EmailHashData{
        val salt = SHACrypto.generateRandomSalt()
        val hash = SHACrypto.hash(email, salt)
        return EmailHashData(salt, hash, false).also{
            hashData = it
        }
    }

    /*
     * Set hashData.confirmed to true if  param hash matches hashData.hash
     * return true if set to true, false if incorrect, null if hashData is null
     */
    fun confirm(hash: ByteArray): Boolean? {
        Logger.singleton.d("hashData = $hashData")
        Logger.singleton.d("hash = $hashData")
        return hashData?.let { hd ->
            hd.hash.contentEquals(hash).also {
                if (it) hashData = hd.copy(confirmed = true)
            }
        }
    }
}