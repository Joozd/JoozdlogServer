package storage

import crypto.AESCrypto
import crypto.SHACrypto
import extensions.readNBytes
import nl.joozd.joozdlogcommon.BasicFlight
import nl.joozd.joozdlogcommon.LoginData
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import nl.joozd.joozdlogcommon.legacy.basicflight.BasicFlightVersionFunctions
import nl.joozd.joozdlogcommon.serializing.intFromBytes
import nl.joozd.joozdlogcommon.serializing.longFromBytes
import settings.Settings
import utils.Logger
import java.io.BufferedInputStream
import java.io.File
import java.lang.Exception
import java.time.Instant

/**
 * IO for encrypted storage for flights
 * Files  structure:
 * - 32 bytes making a hash of the encryption key
 * - 4 bytes stating it's version number (the version of BasicFlight used)
 *      - if version is 0, no flights stored (only hash, version, timestamp)
 * - 8 bytes making a timestamp of when the file was last saved
 */
class FlightsStorage(val loginData: LoginData, private val forcedFlightsFile: FlightsFile? = null) {
    constructor (loginDataWithEmail: LoginDataWithEmail, forcedFlightsFile: FlightsFile? = null):
            this(LoginData(
                userName = loginDataWithEmail.userName,
                password = loginDataWithEmail.password,
                basicFlightVersion = loginDataWithEmail.basicFlightVersion
            ), forcedFlightsFile)


    private val userFilesDirectory
        get() = Settings["userDir"]
    private val log = Logger.singleton
    init{
        println("Init FlightsStorage")
        println("name: ${loginData.userName}")
    }
    private val file = File(userFilesDirectory + loginData.userName).also{
        println(it.absolutePath)}
    private val hash = SHACrypto.hashWithExtraSalt(loginData.userName, loginData.password)
    private val requestedVersion = loginData.basicFlightVersion

    private val fileExists: Boolean
        get() = file.exists()

    val correctKey: Boolean by lazy {
        fileExists && file.readNBytes(32).contentEquals(hash)
    }





    /**
     * Reads file into a FLightsFile object
     * @return null if file not found/not OK, FlightsFile if it is OK
     *
     * unpackWithVersion takes care of upgrading to most recent version,
     */

    val flightsFile: FlightsFile? by lazy { forcedFlightsFile ?:
        if (!correctKey || file.length() < 32+4+8) null // might split this out and add logging or whatever
        else {
            try {
                BufferedInputStream(file.inputStream()).use {
                    it.skip(32) // discard first 32 bytes as they are hash
                    val version = intFromBytes(it.readNBytes(4))
                    val timeStamp = longFromBytes(it.readNBytes(8))
                    val decryptedFlights = AESCrypto.decrypt(loginData.password, it.readAllBytes())
                    println("decrypted ${decryptedFlights?.size} bytes")
                    println("version is $version")


                    val flights = decryptedFlights?.let { serializedFlights ->
                        BasicFlightVersionFunctions.unpackWithVersion(serializedFlights, version)
                    } ?: emptyList()
                    FlightsFile(timeStamp, flights)
                    //TODO put this in a separate upgrader function
                }
            } catch(e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Write stuff to file.
     * @return true if OK, false if error
     */
    private fun writeStuffToFile(stuff: ByteArray): Boolean{
        return try {
            file.writeBytes(stuff)
            true
        } catch (e: Exception){
            e.printStackTrace()
            false
        }
    }


    fun checkKey(){
        if (!fileExists) println("No file")
        else {
            println("Hashed key: \n")
            println("\nExpected key: \n")
            println(file.readNBytes(32).toList())
        }
    }

    fun addFlights(rawData: ByteArray): Int? = flightsFile?.let { fFile ->
        // println("received ${serialized.size} flights")
        val newFlights = BasicFlightVersionFunctions.unpackWithVersion(rawData, requestedVersion)
        fFile.timestamp = Instant.now().epochSecond
        val newFlightIDs = newFlights.map{f -> f.flightID}

        //build list of known flights + new flights, where duplicate IDs are overwritten by new flights
        fFile.flights = fFile.flights.filter{f -> f.flightID !in newFlightIDs} + newFlights
        newFlights.size
    }  // = null if flightsFile is null (not logged in)

    fun writeFlightsToDisk(): Boolean = flightsFile?.let{ fFile ->
        log.d("Encrypting flights...")
        fFile.toEncryptedByteArray(loginData.password)?.let {
            log.d("Writing encrypted flights to disk...")
            writeStuffToFile(hash + it).also{
                log.d("Done writing to disk")
            } // returned `null` will become `false`
        }
    } ?: false // if writeStuffToFile fails, or if flightsFile is null (not logged in)


    fun flightsAsBytes(): ByteArray{
        val ff = flightsFile
        require (ff != null)
        return BasicFlightVersionFunctions.makeVersionAndSerialize(ff.flights, requestedVersion)
    }

    fun filteredFlightsAsBytes(predicate: (BasicFlight) -> Boolean): ByteArray? {
        val ff = flightsFile
        return if (ff == null) null
        else BasicFlightVersionFunctions.makeVersionAndSerialize(ff.flights.filter(predicate), requestedVersion)
    }
}