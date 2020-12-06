package storage

import data.email.EmailHashData
import data.email.EmailHashFile
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import settings.Settings

/**
 * Object to use for retrieving and storing verification data for email addresses
 * No email adresses will be stored, but a combination of username, salt and hash of salt+emailaddress will be stored.
 */
object EmailData {
    private val userFilesDirectory
        get() = Settings["emailDir"]

    fun createEmailDataForUser(username: String, email: String): EmailHashData =
        EmailHashFile(userFilesDirectory + username).create(email)

    fun checkIfEmailConfirmed(username: String, email: String): Boolean = EmailHashFile(userFilesDirectory + username).verifyEmail(email)

    fun checkIfEmailConfirmed(loginDataWithEmail: LoginDataWithEmail) = checkIfEmailConfirmed(loginDataWithEmail.userName, loginDataWithEmail.email)
}