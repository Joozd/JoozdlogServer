package storage

import crypto.SHACrypto
import nl.joozd.joozdlogcommon.BasicFlight
import nl.joozd.joozdlogcommon.LoginData
import nl.joozd.joozdlogcommon.serializing.toByteArray
import utils.Logger
import java.io.File
import java.io.IOException
import java.time.Instant

object UserAdministration {
    const val EMPTY_FLIGHT_LIST = 0
    private val log = Logger.singleton
    /**
     * Checks if a user already exists (if so will return null)
     * Otherwise, it will
     *  - make a directory for that user
     *  - save the users' key as "hash" file
     *  - return a logged-in FlightsStorage object
     *  @param name: username
     *  @param key: AES encryption key as ByteArray
     *  @return null if user exists, FlightsStorage if not
     */
    fun createNewUser(loginData: LoginData): FlightsStorage? {
        if (File("./userfiles/${loginData.userName}").exists()) return null
        File("./userfiles/${ loginData.userName}").createNewFile()
        println("Creating new user: ${loginData.userName}")
        return makeNewFile(loginData)
    }


    private fun makeNewFile(loginData: LoginData, flights: List<BasicFlight>? = null): FlightsStorage? {
        if (!File("./userfiles/${loginData.userName}").exists()) return null
        val hash = SHACrypto.hashWithSalt(loginData.userName, loginData.password)
        val version = EMPTY_FLIGHT_LIST.toByteArray()
        val timestamp = Instant.now().epochSecond
        val timestampBytes = timestamp.toByteArray()
        File("./userfiles/${loginData.userName}").writeBytes(hash + version + timestampBytes)


        return if (File("./userfiles/${loginData.userName}").inputStream().use{it.readNBytes(32)}?.contentEquals(SHACrypto.hashWithSalt(loginData.userName, loginData.password)) == true) {
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


}