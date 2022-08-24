package storage

import crypto.AESCrypto
import crypto.SHACrypto
import extensions.readNBytes
import nl.joozd.joozdlogcommon.BasicFlight
import nl.joozd.joozdlogcommon.LoginData
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import nl.joozd.joozdlogcommon.legacy.basicflight.BasicFlightVersionFunctions
import nl.joozd.serializing.intFromBytes
import nl.joozd.serializing.longFromBytes
import settings.Settings
import utils.Logger
import java.io.BufferedInputStream
import java.io.File
import java.lang.Exception
import java.time.Instant

/**
 * IO for encrypted storage for flights
 * Files  structure:
 * - [HASH_BYTES_SIZE] bytes making a hash of the encryption key
 * - 4 bytes stating it's version number (the version of BasicFlight used)
 *      - if version is 0, no flights stored (only hash, version, timestamp)
 * - 8 bytes making a timestamp of when the file was last saved
 *
 * Upon opening it will restore backups if they exist
 */
class FlightsStorage(val loginData: LoginData, private val forcedFlightsFile: FlightsFile? = null) {
    constructor (loginDataWithEmail: LoginDataWithEmail, forcedFlightsFile: FlightsFile? = null):
            this(LoginData(
                userName = loginDataWithEmail.userName,
                password = loginDataWithEmail.password,
                basicFlightVersion = loginDataWithEmail.basicFlightVersion
            ), forcedFlightsFile)

    private val log = Logger.singleton

    init{
        try {
            restoreAndDeleteBackups()
        } catch (e: SecurityException){
            log.w("Unable to delete baclup files: SecurityException: ${e.message}", this::class.simpleName)
        }
    }

    private val userFilesDirectory
        get() = Settings["userDir"]

    private val file = File(userFilesDirectory + loginData.userName)
    private val hash = generateHashFromLoginData(loginData)
    private val requestedVersion = loginData.basicFlightVersion

    private val fileExists: Boolean
        get() = file.exists()

    val correctKey: Boolean by lazy {
        fileExists && file.readNBytes(HASH_BYTES_SIZE).contentEquals(hash)
    }





    /**
     * Reads file into a FLightsFile object
     * @return null if file not found/not OK, FlightsFile if it is OK
     *
     * unpackWithVersion takes care of upgrading to most recent version,
     */

    val flightsFile: FlightsFile? by lazy {
        forcedFlightsFile ?: when {
            !correctKey -> null.also { log.d("Incorrect key, ${this::class.simpleName}")}

            file.length() < HASH_BYTES_SIZE+4+8 -> null.also { log.d("File too short: ${file.length()}, need ${HASH_BYTES_SIZE+4+8}")}

            else -> loadFLightsFile()
        }
    }



    private fun loadFLightsFile(): FlightsFile? = try {
        BufferedInputStream(file.inputStream()).use {
            it.skip(HASH_BYTES_SIZE.toLong())
            val version = intFromBytes(it.readNBytes(4))
            val timeStamp = longFromBytes(it.readNBytes(8))
            val decryptedFlights = if (version == EMPTY_FLIGHT_LIST) null
            else AESCrypto.decrypt(loginData.password, it.readAllBytes().takeIf { b -> b.isNotEmpty() })
            println("decrypted ${decryptedFlights?.size} bytes")
            println("version is $version")


            val flights = decryptedFlights?.let { serializedFlights ->
                BasicFlightVersionFunctions.unpackWithVersion(serializedFlights, version)
            } ?: emptyList()
            FlightsFile(timeStamp, flights)
        }
    } catch (e: Exception) {
        Logger.singleton.d("Exception when getting flightsfile for user ${loginData.userName}:\n${e.stackTraceToString()}")
        null
    }


    /**
     * Write stuff to file.
     * @return true if OK, false if error
     * Temporary stores data in a backupFile in case server dies halfway
     */
    private fun writeStuffToFile(stuff: ByteArray): Boolean{
        return try {

            // get a fresh destination for backup file
            var backupTries = 0
            var backupFile = File(userFilesDirectory + loginData.userName + BACKUP_APPENDIX)
            while (backupFile.exists()){
                backupFile = File(userFilesDirectory + loginData.userName + BACKUP_APPENDIX + "${backupTries++}")
            }
            //backup old file in case server dies halfway
            file.renameTo(backupFile)
            file.writeBytes(stuff)
            backupFile.delete()
            true
        } catch (e: Exception){
            Logger.singleton.d("Exception when writing to flightsfile for user ${loginData.userName}:\n${e.stackTraceToString()}")
            false
        }
    }


    fun checkKey(){
        if (!fileExists) println("No file")
        else {
            println("Hashed key: \n")
            println("\nExpected key: \n")
            println(file.readNBytes(HASH_BYTES_SIZE).toList())
        }
    }

    //returns amount of flights added or null if flightsFile is null (not logged in)
    @Deprecated("but here for backwards compatibility")
    fun addFlights(rawData: ByteArray): Int? = flightsFile?.let { fFile ->
        // println("received ${serialized.size} flights")
        val newFlights = BasicFlightVersionFunctions.unpackWithVersion(rawData, requestedVersion)
        fFile.timestamp = Instant.now().epochSecond
        val newFlightIDs = newFlights.map{f -> f.flightID}

        //build list of known flights + new flights, where duplicate IDs are overwritten by new flights
        fFile.flights = fFile.flights.filter{f -> f.flightID !in newFlightIDs} + newFlights
        newFlights.size
    }  // = null if flightsFile is null (not logged in)


    //This is the new version. It immediately writes files to disk and doesn't touch timestamps.
    fun addFlights(newFlights: Collection<BasicFlight>): Int? = flightsFile?.let { fFile ->
        val newFlightIDs = newFlights.map{f -> f.flightID}
        //build list of known flights + new flights, where duplicate IDs are overwritten by new flights
        fFile.flights = (fFile.flights.filter{f -> f.flightID !in newFlightIDs} + newFlights).sortedBy { it.flightID }
        writeFlightsToDisk()
        newFlights.size
    }

    fun removeDuplicates(): Boolean{
        flightsFile?.removeDuplicates()
        return writeFlightsToDisk()
    }

    fun writeFlightsToDisk(): Boolean = flightsFile?.let{ fFile ->
        log.d("Encrypting flights...")
        fFile.toEncryptedByteArray(loginData.password)?.let {
            log.d("Writing encrypted flights to disk...")
            writeStuffToFile(hash + it).also{
                log.d("Done writing to disk")
            } // returned `null` will become `false`
        }
    } ?: false // if writeStuffToFile fails, or if flightsFile is null (not logged in)

    fun filteredFlightsAsBytes(predicate: (BasicFlight) -> Boolean): ByteArray? {
        val ff = flightsFile
        return if (ff == null) null
        else BasicFlightVersionFunctions.makeVersionAndSerialize(ff.flights.filter(predicate), requestedVersion)
    }


    /**
     * Restures and deletes any backups found
     * @return true if a backup was restored, false if not
     * @throws SecurityException if deleting backup files is denied by [SecurityManager.checkDelete]
     */
    private fun restoreAndDeleteBackups(): Boolean{
        var backupFile = File(userFilesDirectory + loginData.userName + BACKUP_APPENDIX)
        if (!backupFile.exists()) return false
        var counter = 0
        while (File(userFilesDirectory + loginData.userName + BACKUP_APPENDIX + "${counter++}").exists()){
            backupFile = File(userFilesDirectory + loginData.userName + BACKUP_APPENDIX + "${counter++}")
        }
        file.delete()
        backupFile.renameTo(file)
        counter = 0
        while (File(userFilesDirectory + loginData.userName + BACKUP_APPENDIX + "${counter++}").exists()){
            File(userFilesDirectory + loginData.userName + BACKUP_APPENDIX + "${counter++}").delete()
        }
        return true
    }
    companion object{
        // Static function so other functions (ie. creating new file) can use this as well
        fun generateHashFromLoginData(loginData: LoginData): ByteArray =
            SHACrypto.hashWithExtraSalt(loginData.userName, loginData.password)
        const val EMPTY_FLIGHT_LIST = 0
        const val BACKUP_APPENDIX = ".backup"

        const val HASH_BYTES_SIZE = 32
    }
}