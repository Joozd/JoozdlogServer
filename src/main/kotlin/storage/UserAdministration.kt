package storage

import crypto.SHACrypto
import extensions.readNBytes
import nl.joozd.joozdlogcommon.BasicFlight
import nl.joozd.joozdlogcommon.LoginData
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import nl.joozd.joozdlogcommon.serializing.toByteArray
import settings.Settings
import utils.Logger
import utils.RandomGenerator
import java.io.File
import java.io.IOException
import java.time.Instant

object UserAdministration {
    private const val EMPTY_FLIGHT_LIST = 0
    private const val USERNAME_LENGTH = 16 // characters. [a-zA-z0-9]
    private val USERNAME_CHARACTERS = ('a'..'z').joinToString("") + ('A'..'Z').joinToString("") + ('0'..'9').joinToString("")

    private val log = Logger.singleton
    private val userFilesDirectory
        get() = Settings["userDir"]

    /**
     * Checks if a user already exists (if so will return null)
     * Otherwise, it will
     *  - make a directory for that user
     *  - save the users' key as "hash" file
     *  - return a logged-in FlightsStorage object
     *  @param loginData: [LoginData] object with login data     
     *  @return null if user exists, FlightsStorage if not
     */
    fun createNewUser(loginData: LoginData): FlightsStorage? {
        if (File(userFilesDirectory + loginData.userName).exists()) return null
        File(userFilesDirectory + loginData.userName).createNewFile()
        println(userFilesDirectory + loginData.userName)
        return makeNewFile(loginData)
    }

    /**
     *  Generates a username that is not yet in use
     */
    fun generateUserName(): String{
        var randomUsername: String
        do { randomUsername = RandomGenerator(USERNAME_CHARACTERS).generateCode(USERNAME_LENGTH) }
        while (File(userFilesDirectory + randomUsername).exists())
        return randomUsername
    }

    private fun makeNewFile(loginData: LoginData, flights: List<BasicFlight>? = null): FlightsStorage? {
        if (!File(userFilesDirectory + loginData.userName).exists()) return null
        val hash = SHACrypto.hashWithExtraSalt(loginData.userName, loginData.password)
        val version = EMPTY_FLIGHT_LIST.toByteArray()
        val timestamp = Instant.now().epochSecond
        val timestampBytes = timestamp.toByteArray()
        File(userFilesDirectory + loginData.userName).writeBytes(hash + version + timestampBytes)


        return if (File(userFilesDirectory + loginData.userName).inputStream().use{it.readNBytes(32)}?.contentEquals(SHACrypto.hashWithExtraSalt(loginData.userName, loginData.password)) == true) {
            println("password set for user ${loginData.userName}")
            FlightsStorage(loginData, FlightsFile(timestamp, flights?: emptyList()))
        }
        else null
    }

    /**
     * Updates a users password and re-encrypts it's flights
     */
    fun updatePassword(flightsStorage: FlightsStorage, newKey: ByteArray): FlightsStorage? {
        if (!flightsStorage.correctKey) return null
        val knownFlights = flightsStorage.flightsFile?.flights
        knownFlights?.let{ kf ->
            val newLoginData = flightsStorage.loginData.copy(password =  newKey)
            return makeNewFile(newLoginData, kf)?.apply {
                log.n("writing files to disk...")
                if (!writeFlightsToDisk()) throw (IOException("Unable to write flights to disk"))
                log.d("returning from updatePassword")
            }
        }
        throw (IOException("corrupt file or read error"))
    }

    fun checkLoginValid(loginData: LoginData): Boolean{
        if (!File(userFilesDirectory + loginData.userName).exists()) return false
        val hash = SHACrypto.hashWithExtraSalt(loginData.userName, loginData.password)

        return File(Settings["userDir"] + loginData.userName).readNBytes(32).contentEquals(hash)
    }

    fun checkLoginValid(loginData: LoginDataWithEmail) = with (loginData) { checkLoginValid(LoginData(userName, password, basicFlightVersion)) }

}