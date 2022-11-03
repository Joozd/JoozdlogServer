@file:Suppress("DEPRECATION") // LoginDataWithEmail is deprecated, it's here for migration purposes. Gets removed after 2023-12-01

package data.email

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import settings.Settings
import utils.CoroutineTimerTask
import utils.Logger
import kotlin.time.Duration.Companion.days

/**
 * Object to use for retrieving and storing verification data for email addresses
 * No email adresses will be stored, but a combination of username, salt and hash of salt+emailaddress will be stored.
 */
object EmailRepository {
    private val userFilesDirectory
        get() = Settings["emailDir"]!!

    fun getHashFileForUser(username: String, dir: String = userFilesDirectory): EmailHashFile =
        EmailHashFile(dir + username)

    fun checkIfEmailConfirmed(id: Long, emailAddress: String): Boolean {
        val emailData = EmailRecords.getInstance().getEmailDataRecordForUser(id) ?: return false
        return emailData matches emailAddress
    }

    //mailDataRecord is removed if not confirmed after 86400 seconds
    fun registerNewEmail(emailAddress: String): EmailDataRecord =
        EmailRecords.getInstance()
            .addOrUpdateEmailData(
                EmailDataRecord.generate(emailAddress)
            ).also{
                CoroutineTimerTask.start(
                    name = "Remove record for ${it.id} after 1 day if not verified",
                    delay = 1.days
                ){
                    withContext(Dispatchers.IO) { EmailRecords.getInstance().deleteIfNotVerified(it) }
                    Logger.singleton.d("CoroutineTimerTask for deleting email data with id ${it.id} completed!")
                }
            }

    fun migrateEmail(dataToMigrate: LoginDataWithEmail): EmailDataRecord? = try {
        EmailRecords.getInstance().copyEmailFromLegacy(
            dataToMigrate.userName,
            dataToMigrate.email
        )
    } catch (e: EmailRecords.UserNotFoundException){
        null
    }

    // returns true for good hash, false for bad hash, null for ID not found.
    fun confirmEmail(confirmationString: String) =
        EmailRecords.getInstance()
            .confirmEmail(confirmationString)
}