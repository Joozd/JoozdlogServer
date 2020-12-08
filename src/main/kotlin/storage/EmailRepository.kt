package storage

import data.email.EmailHashData
import data.email.EmailHashFile
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import settings.Settings

/**
 * Object to use for retrieving and storing verification data for email addresses
 * No email adresses will be stored, but a combination of username, salt and hash of salt+emailaddress will be stored.
 */
object EmailRepository {
    private val userFilesDirectory
        get() = Settings["emailDir"]

    fun createEmailDataForUser(username: String, email: String): EmailHashData =
        EmailHashFile(userFilesDirectory + username).create(email)

    fun checkIfEmailConfirmed(username: String, email: String): Boolean = EmailHashFile(userFilesDirectory + username).verifyEmail(email)

    fun checkIfEmailConfirmed(loginDataWithEmail: LoginDataWithEmail) = checkIfEmailConfirmed(loginDataWithEmail.userName, loginDataWithEmail.email)

    /**
     * check if hash matches if so, confirm it.
     * @return true if set to true, false if incorrect, null if hashData is null (eg. file does not exist)
     */
    fun tryToConfirmEmail(username: String, hash: ByteArray): Boolean? =
        EmailHashFile(userFilesDirectory + username).confirm(hash)


    /**
     * reeeealy basic email address checker
     */
    fun checkIfValidEmailAddress(email: String) = ".+@.+\\..+".toRegex() matches email


}