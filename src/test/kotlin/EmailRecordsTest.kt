import data.email.EmailDataRecord
import data.email.EmailRecords
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailRecordsTest {
    @Test
    fun testEmailHashes(){
        val testAddress = "jemoeder@snor.nu"
        val wrongAddress = "peters.moeder@snor.nu"
        val testData = EmailDataRecord.generate(testAddress)
        println("DB should be in ${System.getProperty("user.dir")}/${EmailRecords.DATABASE_LOCATION}")
        runBlocking{
            with (EmailRecords.getInstance()) {
                println("Currently in DB:\n${getAllEmailData().joinToString("\n")}")
                val savedData = addOrUpdateEmailData(testData)
                val newData = getEmailDataRecordForUser(savedData.id)
                assertEquals(testData.copy(id = savedData.id), newData)
                assertEquals(null, getEmailDataRecordForUser(testData.id))

                val updatedData = savedData.copy(isVerified = true)
                addOrUpdateEmailData(updatedData)
                val checkData = getEmailDataRecordForUser(savedData.id)
                assertEquals(updatedData, checkData)


                assert(updatedData matches testAddress)
                assert(!updatedData.matches(wrongAddress))
                println("\'vo!")
            }
        }
    }
}