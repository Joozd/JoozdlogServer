package server

import nl.joozd.joozdlogcommon.LoginData
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsKeywords
import nl.joozd.joozdlogcommon.serializing.*
import storage.FlightsStorage
import storage.UserAdministration
import utils.Logger
import java.io.Closeable
import kotlin.collections.toByteArray

/**
 * Handler should get an open IOWorker upon construction
 * It will use this socket to do all kinds of things.
 * Handler should be closed after usage, which will close the IOWorker
 * @param socket: an open IOWorker to be used for communications
 */

class Handler(private val socket: IOWorker): Closeable {
    private val log = Logger.singleton

    private val TAG: String?
        get() = this::class.simpleName




    fun handleAll(){
        var keepGoing = true
        var flightsStorage: FlightsStorage? = null

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var protocolVersion: Int? = null // not used at the moment, but useful when updating protocol

        while (keepGoing) {
            val receivedMessage = socket.read()
            if (nextType(receivedMessage) == STRING){                       // check if package seems to start with a string
                val request = unwrapString(nextWrap(receivedMessage))
                val extraData = receivedMessage.slice(wrap(request).size until receivedMessage.size).toByteArray()
                log.d("DEBUG: received request: $request from ${socket.clientAddress}")
                when (request){ // keep this in same order as JoozdlogComsKeywords pls
                    JoozdlogCommsKeywords.HELLO -> {
                        @Suppress("UNUSED_VALUE")
                        protocolVersion = intFromBytes(extraData)
                    } // not used at the moment, but useful when updating protocol

                    // JoozdlogCommsKeywords.NEXT_IS_COMPRESSED -> TODO("not implemented")

                    JoozdlogCommsKeywords.REQUEST_TIMESTAMP -> ServerFunctions.sendTimestamp(socket)

                    /**
                     * Login will have extraData:
                     * <wrapped string>USERNAME</><bytearray>ENCRYPTION_KEY</bytearray>
                     */
                    JoozdlogCommsKeywords.LOGIN -> {
                        val loginData = LoginData.deserialize(extraData)
                        flightsStorage = FlightsStorage(loginData)
                        log.n("login attempt for ${loginData.userName} from ${socket.clientAddress}", LOGIN_TAG)
                        log.n(if (flightsStorage.correctKey) "success" else "failed", LOGIN_TAG)
                        /*
                        - Server checks if file [username] exists, if so, it loads flights/aircraft from files
                        - server responds "OK" or "UNKNOWN_USER"<make registration for users?> or "WRONG_PASSWORD"
                        */
                        if (flightsStorage.correctKey)
                            socket.write(JoozdlogCommsKeywords.OK)
                        else {
                            flightsStorage.checkKey()
                            socket.write(JoozdlogCommsKeywords.UNKNOWN_USER_OR_PASS)
                        }
                    }


                    /**
                     * Creates a new user directory with password
                     * extraData should be <LoginData>(serialized)
                     */
                    JoozdlogCommsKeywords.NEW_ACCOUNT -> {
                        val loginData = LoginData.deserialize(extraData)
                        flightsStorage = UserAdministration.createNewUser(loginData)
                        if (flightsStorage == null)
                            socket.write(JoozdlogCommsKeywords.USER_ALREADY_EXISTS)
                        else
                            socket.write(JoozdlogCommsKeywords.OK).also{
                                log.n("Created new user: ${loginData.userName}", NEW_USER_TAG)
                            }
                    }

                    /**
                     * Creates a new user directory with password
                     * extraData should be <LogindataWithEmail>(serialized)
                     */
                    JoozdlogCommsKeywords.NEW_ACCOUNT_EMAIL -> {
                        LoginDataWithEmail.deserialize(extraData).let {ld ->
                            flightsStorage = UserAdministration.createNewUser(LoginData(ld.userName, ld.password, ld.basicFlightVersion))
                            if (flightsStorage == null)
                                socket.write(JoozdlogCommsKeywords.USER_ALREADY_EXISTS)
                            else
                                socket.write(JoozdlogCommsKeywords.OK).also {
                                    log.n("Created new user: ${ld.userName}", NEW_USER_TAG)
                                    if (ServerFunctions.sendLoginLinkEmail(ld))
                                        log.v("Sent login link to ${ld.email}", NEW_USER_TAG)
                                }
                        }
                    }

                    /**
                     * Changes a users password and deletes saved data
                     */
                    JoozdlogCommsKeywords.CHANGE_PASSWORD -> TODO("not implemented")

                    /**
                     * Decrypts users flights, changes its password, and encrypts data with new pass
                     * On success updates [flightsStorage]
                     * Expects a [LoginDataWithEmail] with key and possible email as [extraData]
                     */
                    JoozdlogCommsKeywords.UPDATE_PASSWORD -> {
                        ServerFunctions.changePassword(socket, flightsStorage, extraData)?.let{
                            flightsStorage = it
                        }
                    }


                    /**
                     * Send a backup mail to user
                     * [extraData] is [LoginDataWithEmail]
                     * flightsStorage can be null, in that case login from loginData will be used.
                     */
                    JoozdlogCommsKeywords.REQUEST_BACKUP_MAIL -> ServerFunctions.sendBackupMail(socket, flightsStorage, extraData)


                    /**
                     * expecting a bunch of packed serialized BasicFlights
                     * Will only work after logging in as otherwise no storage loaded
                     * [extraData] should be formatted as packSerialized(basicFlightsList.map{it.serialize()})
                     */

                    //TODO work should be done by worker not by handler
                    JoozdlogCommsKeywords.SENDING_FLIGHTS -> {
                        flightsStorage?.let{storage ->
                            storage.addFlights(extraData)?.let {
                                socket.write(JoozdlogCommsKeywords.OK)
                                log.v("received $it flights from client", FLIGHTS_FILE_TAG)
                            } ?: run {
                                log.e("error while adding flights from client ${socket.clientAddress}", FLIGHTS_FILE_TAG)
                                socket.write(JoozdlogCommsKeywords.SERVER_ERROR)
                            }
                        } ?: socket.write(JoozdlogCommsKeywords.NOT_LOGGED_IN).also{
                            log.w("user at ${socket.clientAddress} wanted to send flights while not logged in", FLIGHTS_FILE_TAG)
                        }
                    }

                    /**
                     * expects a single Long as extraData, being a timestamp.
                     * Will return a packed list of serialized BasicFlights, filtered by NEWER THAN timestamp (not equal)
                     */
                    JoozdlogCommsKeywords.REQUEST_FLIGHTS_SINCE_TIMESTAMP  -> {
                        if (flightsStorage?.correctKey != true)
                            socket.write(JoozdlogCommsKeywords.NOT_LOGGED_IN)
                        flightsStorage?.flightsFile?.let{ff ->
                            val timeStamp = unwrapLong(extraData)
                            log.v("sending ${ff.flights.filter { it.timeStamp > timeStamp }.size} flights to client", TAG)
                            socket.write(flightsStorage?.filteredFlightsAsBytes { it.timeStamp > timeStamp } ?: JoozdlogCommsKeywords.NOT_LOGGED_IN.toByteArray(Charsets.UTF_8))
                        } ?: socket.write(JoozdlogCommsKeywords.SERVER_ERROR).also{
                            log.e("server error while sending flights to ${socket.clientAddress}")
                        }
                    }


                    /**
                     * receive consensus data to add or remove from consensus
                     * [extraData] should be packedList of ConsensusData.serialize()
                     */
                    JoozdlogCommsKeywords.SENDING_AIRCRAFT_CONSENSUS -> ServerFunctions.addCToConsensus(socket, extraData)

                    JoozdlogCommsKeywords.REQUEST_AIRCRAFT_CONSENSUS -> ServerFunctions.getAircraftConsensus(socket)

                    JoozdlogCommsKeywords.REQUEST_AIRCRAFT_TYPES -> ServerFunctions.sendAircraftTypes(socket)

                    JoozdlogCommsKeywords.REQUEST_AIRCRAFT_TYPES_VERSION -> ServerFunctions.sendAircraftTypesVersion(socket)

                    JoozdlogCommsKeywords.REQUEST_FORCED_TYPES_VERSION -> ServerFunctions.sendForcedTypesVersion(socket)

                    JoozdlogCommsKeywords.REQUEST_FORCED_TYPES -> ServerFunctions.sendForcedTypes(socket)






                    /**
                     * Expects a single Long as `extraData`, to be taken as timestamp for this session
                     * Use this after sending flights as sending flights will update timestamp as well
                     */

                    JoozdlogCommsKeywords.ADD_TIMESTAMP -> {
                        val timestamp = unwrapLong(extraData)
                        flightsStorage?.flightsFile?.timestamp = timestamp
                        if (flightsStorage?.flightsFile?.timestamp == timestamp) {// not in case left half is null
                            socket.write(JoozdlogCommsKeywords.OK)
                            log.d("Timestamp successfully updated to $timestamp")
                        }
                        else {
                            socket.write(JoozdlogCommsKeywords.SERVER_ERROR)
                            log.e("error: Timestamp doesn't match.\nflightsStorage: $flightsStorage\ntimestamp: ${flightsStorage?.flightsFile?.timestamp}", FLIGHTS_FILE_TAG)
                        }
                    }

                    JoozdlogCommsKeywords.REQUEST_AIRPORT_DB_VERSION -> ServerFunctions.sendAirportDbVersion(socket)

                    JoozdlogCommsKeywords.REQUEST_AIRPORT_DB -> ServerFunctions.sendAirportDb(socket)


                    JoozdlogCommsKeywords.SAVE_CHANGES -> {
                        flightsStorage?.let { storage ->
                            if (storage.writeFlightsToDisk()) {
                                socket.write(JoozdlogCommsKeywords.OK)
                                log.v("Successfully saved all ${storage.flightsFile?.flights?.size} flights to disk for user ${storage.loginData.userName}", FLIGHTS_FILE_TAG)
                            } else {
                                log.e("Write problem while writing flights to disk for ${socket.clientAddress}")
                                socket.write(JoozdlogCommsKeywords.SERVER_ERROR)
                            }
                        }
                    }

                    JoozdlogCommsKeywords.DEBUG_SEND_TEST_MAIL -> {
                        ServerFunctions.sendTestEmail(socket)
                    }

                    JoozdlogCommsKeywords.END_OF_SESSION -> {
                        keepGoing = false
                        log.d("Closing connection.\n")
                        socket.close()
                    }

                    else -> {                                               // unknown request will close connection
                        log.v("unknown request: $request from ${socket.clientAddress}, closing connection")
                        keepGoing = false
                    }
                }


            } else {
                log.v("Invalid request from ${socket.clientAddress}, stopping handleAll()")
                keepGoing = false
            }
                                
        }
    }

    override fun close() = socket.close()

    companion object{
        private const val LOGIN_TAG =     "Login"
        private const val NEW_USER_TAG =  "New User"
        private const val FLIGHTS_FILE_TAG = "FlightsFile"

    }
}