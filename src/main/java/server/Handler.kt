package server

import nl.joozd.joozdlogcommon.BasicFlight
import nl.joozd.joozdlogcommon.LoginData
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsKeywords
import nl.joozd.joozdlogcommon.serializing.*
import storage.AirportsStorage
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


    fun handleAll(){
        var keepGoing = true
        var flightsStorage: FlightsStorage? = null
        var protocolVersion: Int? = null

        while (keepGoing) {
            val receivedMessage = socket.read()
            if (nextType(receivedMessage) == STRING){                       // check if package seems to start with a string
                val request = unwrapString(nextWrap(receivedMessage))
                val extraData = receivedMessage.slice(wrap(request).size until receivedMessage.size).toByteArray()
                log.d("DEBUG: received request: $request from ${socket.clientAddress}")
                when (request){ // keep this in same order as JoozdlogComsKeywords pls
                    JoozdlogCommsKeywords.HELLO -> protocolVersion = intFromBytes(extraData)

                    JoozdlogCommsKeywords.NEXT_IS_COMPRESSED -> TODO("not implemented")

                    JoozdlogCommsKeywords.REQUEST_TIMESTAMP -> ServerFunctions.sendTimestamp(socket)

                    /**
                     * Login will have extraData:
                     * <wrapped string>USERNAME</><bytearray>ENCRYPTION_KEY</bytearray>
                     */
                    JoozdlogCommsKeywords.LOGIN -> {
                        val loginData = LoginData.deserialize(extraData)
                        flightsStorage = FlightsStorage(loginData)
                        log.n("login for ${loginData.userName} ok = ${flightsStorage.correctKey}")
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
                     * extraData should be<WString>USERNAME</><ByteArray>ENCRYPTION_KEY</bytearray>
                     */
                    JoozdlogCommsKeywords.NEW_ACCOUNT -> {
                        val loginData = LoginData.deserialize(extraData)
                        flightsStorage = UserAdministration.createNewUser(loginData)
                        if (flightsStorage == null)
                            socket.write(JoozdlogCommsKeywords.USER_ALREADY_EXISTS)
                        else
                            socket.write(JoozdlogCommsKeywords.OK)
                    }

                    /**
                     * Changes a users password and deletes saved data
                     */
                    JoozdlogCommsKeywords.CHANGE_PASSWORD -> TODO("not implemented")

                    /**
                     * Decrypts users flights, changes its password, and encrypts data with new pass
                     */
                    JoozdlogCommsKeywords.UPDATE_PASSWORD -> TODO("not implemented")


                    /**
                     * expecting a bunch of packed serialized BasicFlights
                     * Will only work after logging in as otherwise no storage loaded
                     * [extraData] should be formatted as packSerialized(basicFlightsList.map{it.serialize()})
                     */

                    //TODO work should be done by worker not by handler
                    JoozdlogCommsKeywords.SENDING_FLIGHTS -> {
                        flightsStorage?.let{storage ->
                            if (storage.addFlights(extraData)) {
                                println("saved OK")
                                socket.write(JoozdlogCommsKeywords.OK)
                            }
                            else {
                                println("error while saving")
                                socket.write(JoozdlogCommsKeywords.SERVER_ERROR)
                            }
                        } ?: socket.write(JoozdlogCommsKeywords.NOT_LOGGED_IN)
                    }

                    /**
                     * expects a single Long as extraData, being a timestamp.
                     * Will return a packed list of serialized BasicFlights, filtered by NEWER THAN timestamp (not equal)
                     */
                    JoozdlogCommsKeywords.REQUEST_FLIGHTS_SINCE_TIMESTAMP  -> {
                        if (flightsStorage?.correctKey != true)
                            socket.write(JoozdlogCommsKeywords.NOT_LOGGED_IN)
                        if (flightsStorage?.flightsFile == null)
                            socket.write(JoozdlogCommsKeywords.SERVER_ERROR)
                        else{
                            val timeStamp = unwrapLong(extraData)
                            println("available flights: ${flightsStorage.flightsFile?.flights?.size}")
                            println("sending ${flightsStorage.flightsFile?.flights?.filter { it.timeStamp > timeStamp }?.size} flights!")
                            socket.write(flightsStorage.filteredFlightsAsBytes { it.timeStamp > timeStamp } ?: JoozdlogCommsKeywords.NOT_LOGGED_IN.toByteArray(Charsets.UTF_8))
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
                            println("Timestamp successfully updated to $timestamp")
                        }
                        else {
                            socket.write(JoozdlogCommsKeywords.SERVER_ERROR)
                            println("error: Timestamp doesn't match.\nflightsStorage: $flightsStorage\ntimestamp: ${flightsStorage?.flightsFile?.timestamp}")
                        }
                    }

                    JoozdlogCommsKeywords.REQUEST_AIRPORT_DB_VERSION -> ServerFunctions.sendAirportDbVersion(socket)

                    JoozdlogCommsKeywords.REQUEST_AIRPORT_DB -> ServerFunctions.sendAirportDb(socket)


                    JoozdlogCommsKeywords.SAVE_CHANGES -> {
                        flightsStorage?.let { storage ->
                            if (storage.writeFlightsToDisk()) {
                                socket.write(JoozdlogCommsKeywords.OK)
                                println("Successfully wrote ${storage.flightsFile?.flights?.size} flights! to disk for user ${flightsStorage.loginData.userName}")
                            } else {
                                println("Write problem")
                                socket.write(JoozdlogCommsKeywords.SERVER_ERROR)
                            }
                        }
                    }

                    JoozdlogCommsKeywords.END_OF_SESSION -> {
                        keepGoing = false
                        println("Closing connection.\n")
                        socket.close()
                    }

                    else -> {                                               // unknown request will close connection
                        println("unknown request: $request, closing connection")
                        keepGoing = false
                    }
                }





            } else {
                println("Invalid request, stopping handleAll()")
                keepGoing = false
            }
                                
        }
    }

    override fun close() = socket.close()
}