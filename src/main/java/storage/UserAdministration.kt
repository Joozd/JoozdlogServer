package storage

import crypto.SHACrypto
import nl.joozd.joozdlogcommon.LoginData
import nl.joozd.joozdlogcommon.serializing.toByteArray
import java.io.File
import java.io.IOException
import java.time.Instant

object UserAdministration {
    const val EMPTY_FLIGHT_LIST = 0
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

    /**
     * Changes a users password
     * What this actually does:
     * - overwrite "flights" file with new hash and timestamp
     *  @param name: username
     *  @param key: AES encryption key as ByteArray
     *  @return FlightsStorage if user exists, null if not or if somehow saving hash failed
     */
    private fun makeNewFile(loginData: LoginData): FlightsStorage? {
        if (!File("./userfiles/${loginData.userName}").exists()) return null

        File("./userfiles/${loginData.userName}").outputStream().use{
            val hash = SHACrypto.hashWithSalt(loginData.userName, loginData.password)
            val version = EMPTY_FLIGHT_LIST.toByteArray()
            val timestamp = Instant.now().epochSecond.toByteArray()
            it.write(hash + version + timestamp)
            it.flush()
        }
        return if (File("./userfiles/${loginData.userName}").inputStream().use{it.readNBytes(32)}?.contentEquals(SHACrypto.hashWithSalt(loginData.userName, loginData.password)) == true) {
            println("password set for user ${loginData.userName}")
            FlightsStorage(loginData)
        }
        else null
    }

    /**
     * Updates a users password and re-encrypts it's flights
     */
    fun updatePassword(flightsStorage: FlightsStorage, newKey: ByteArray): FlightsStorage? {
        if (!flightsStorage.correctKey) return null
        val knownFlights = flightsStorage.flightsFile?.flights
        knownFlights?.let{
            val newLoginData = flightsStorage.loginData.copy(password =  newKey)
            makeNewFile(newLoginData)
            return FlightsStorage(newLoginData).apply {
                if (!writeFlightsToDisk()) throw (IOException("Unable to write flights to disk"))
            }
        }
        throw (IOException("corrupt file or read error"))
    }


}