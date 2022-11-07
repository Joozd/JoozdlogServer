package data.email

import crypto.SHACrypto
import nl.joozd.joozdlogcommon.EmailData
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import utils.Logger
import java.time.Instant

//This takes care of lastAccessed tracking.
class EmailRecords private constructor() {
    private val now get() = Instant.now().epochSecond
    /**
     * Users with legacy email data ca just copy their data and receive an ID instead of a username, no need to re-confirm.
     * As hashes are now without salt, email will be rehashed, cannot just copy.
     */
    fun copyEmailFromLegacy(username: String, emailAddress: String): EmailDataRecord{
        val emailHashFile = EmailRepository.getHashFileForUser(username, "C:/joozdlog/test/email") // hardcoded path for testing only
        val now = Instant.now().epochSecond
        return transaction {
            emailHashFile.let {
                //check if correct email address:
                if (it.verifyEmail(emailAddress)) {
                    it.hashData?.let { h ->
                        println("DEBUG: ${it.username}")
                        println("confirmed: ${h.confirmed}")
                        val salt = SHACrypto.generateRandomSalt()
                        val emailData = EmailDataRecord(
                            id = EmailData.EMAIL_ID_NOT_SET,
                            hash = SHACrypto.hash(emailAddress, salt),
                            salt = salt,
                            lastAccessed = now,
                            isVerified = h.confirmed
                        )
                        newEmailData(emailData)
                    }
                }
                else throw (UserNotFoundException("Username $username and email address $emailAddress do not match"))
            }
        } ?: throw (UserNotFoundException("User $username not found in legacy data"))
    }

    //This doesn't update lastAccessed
    fun getAllEmailData(): List<EmailDataRecord> =
        transaction {
            EmailDataDao.all().map { EmailDataRecord(it) }
        }


    fun getEmailDataRecordForUser(id: Long): EmailDataRecord? =
        transaction {
            EmailDataDao.findById(id)?.let {
                it.lastAccessed = now
                EmailDataRecord(it)
            }
        }

    /**
     * Adds or updates email data (id, address, hashcode, timestamp for last used and isVerified)
     * @return the EmailData that was just stored.
     */
    fun addOrUpdateEmailData(emailData: EmailDataRecord): EmailDataRecord =
        transaction {
            if (emailData.id == EmailData.EMAIL_ID_NOT_SET)
                newEmailData(emailData)
            else
                updateEmailData(emailData)
        }

    // returns true for good hash, false for bad hash, null for ID not found.
    // This only updates lastAccessed on good hash
    fun confirmEmail(confirmationString: String): Boolean? {
        val id = getIDFromConfirmationString(confirmationString)
        val recordToConfirm = transaction { EmailDataDao.findById(id) } ?: return null
        return (confirmationString == EmailDataRecord(recordToConfirm).confirmationString()).also{
            if (it)
                markAsVerified(recordToConfirm)
        }
    }

    private fun markAsVerified(recordToConfirm: EmailDataDao) {
        transaction {
            recordToConfirm.lastAccessed = now
            recordToConfirm.isVerified = true
        }
    }

    fun deleteIfNotVerified(record: EmailDataRecord){
        transaction {
            EmailDataDao.findById(record.id)?.apply{
                if (!isVerified)
                    delete()
            }
        } ?: throw(UserNotFoundException("User ${record.id} not found in database"))
    }


    private fun getIDFromConfirmationString(confirmationString: String) =
        confirmationString.split(':').first().toLong()

    @Suppress("unused") //unused receiver is there to make sure this is only used inside a transaction.
    private fun Transaction.newEmailData(emailData: EmailDataRecord): EmailDataRecord{
        require(emailData.id == EmailData.EMAIL_ID_NOT_SET) // otherwise use Transaction.updateEmailData
        return EmailDataRecord(EmailDataDao.new {
            hash = emailData.hash
            salt = emailData.salt
            lastAccessed = now
            isVerified = emailData.isVerified
        })
    }

    @Suppress("unused") //unused receiver is there to make sure this is only used inside a transaction.
    private fun Transaction.updateEmailData(emailData: EmailDataRecord): EmailDataRecord = with(emailData){
        require(id != EmailData.EMAIL_ID_NOT_SET) // otherwise use Transaction.newEmailData
        val storedEmailData = EmailDataDao.findById(id) ?: throw(UserNotFoundException("User $id not found in database"))

        if(!hash.contentEquals(storedEmailData.hash)) storedEmailData.hash = hash
        if(!salt.contentEquals(storedEmailData.salt)) storedEmailData.salt = salt
        if(lastAccessed != storedEmailData.lastAccessed) storedEmailData.lastAccessed = now
        if(isVerified != storedEmailData.isVerified) storedEmailData.isVerified = isVerified

        return EmailDataRecord(storedEmailData)
    }


    class UserNotFoundException(message: String): Exception(message)


    companion object{
        const val DATABASE_LOCATION = "../data/email_db"

        private val INSTANCE by lazy{ EmailRecords() }
        private var initialized = false
        fun getInstance(): EmailRecords =
            if (initialized)
                INSTANCE
            else {
                Logger.singleton.d("Making an email DB instance....")
                Database.connect("jdbc:h2:file:$DATABASE_LOCATION")
                Logger.singleton.d("Connected to DB!")
                transaction {
                    SchemaUtils.create(EmailDataTable)
                }
                Logger.singleton.d("made an email DB instance!")
                INSTANCE
            }
    }
}