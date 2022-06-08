package server.serverFunctions

import data.aircraft.AircraftTypesConsensus
import data.feedback.FeedbackHandler
import extensions.sendError
import nl.joozd.comms.IOWorker
import nl.joozd.joozdlogcommon.*
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsKeywords
import nl.joozd.serializing.*
import storage.AirportsStorage
import storage.EmailRepository
import storage.FlightsStorage
import storage.UserAdministration
import utils.Logger
import java.io.IOException
import java.time.Instant
import java.util.*


object ServerFunctions {
    private const val CONSENSUS_TAG = "Consensus"
    private const val CHANGE_PASSWORD_TAG = "ChangePassword"

    private val log = Logger.singleton

    /**
     * Sends current time as to client as seconds since epoch
     * @param socket: IOWorker to take care of transmission to client
     * @return true if success, false if exception caught
     */
    fun sendTimestamp(socket: IOWorker): Boolean =
        socket.write(Instant.now().epochSecond.toByteArray())

    /**
     * Generate a username and send it to client in the form of a string.
     */
    fun sendNewUsername(socket: IOWorker): Boolean =
        socket.write(UserAdministration.generateUserName())

    /**
     * Sends current AircraftTypesConsensus.aircraftTypes to client
     * @param socket: IOWorker to take care of transmission to client
     * @return true if success, false if exception caught
     */
    fun sendAircraftTypes(socket: IOWorker): Boolean {
        val acTypes: List<AircraftType> = AircraftTypesConsensus.getInstance().aircraftTypes
        val payload = packSerialized(acTypes.map { it.serialize() })
        return socket.write(payload)
    }

    fun sendAircraftTypesVersion(socket: IOWorker): Boolean {
        val version: Int = AircraftTypesConsensus.getInstance().aircraftTypesVersion
        val payload = wrap(version)
        return socket.write(payload)
    }

    fun sendForcedTypesVersion(socket: IOWorker): Boolean {
        val version: Int = AircraftTypesConsensus.getInstance().forcedTypesVersion
        val payload = wrap(version)
        return socket.write(payload)
    }

    fun sendForcedTypes(socket: IOWorker): Boolean {
        val payload = AircraftTypesConsensus.getInstance().serializedForcedTypes
        return socket.write(payload)
    }

    fun receiveFeedback(socket: IOWorker, extraData: ByteArray){
        deserializeFeedbackData(extraData)?.let { feedbackData ->
            FeedbackHandler.instance.addFeedback(feedbackData)
            socket.ok()
        } ?: socket.sendError(JoozdlogCommsKeywords.BAD_DATA_RECEIVED).also { log.w("Bad data received from ${socket.otherAddress}", "receiveFeedback")}
    }


    /**
     * Add or remove consensus data to or from a registration
     */
    fun addCToConsensus(socket: IOWorker, extraData: ByteArray) {
        with(AircraftTypesConsensus.getInstance()) {
            unpackSerialized(extraData).map { ConsensusData.deserialize(it) }
                .also {
                    log.v("Received ${it.size} Consensus instructions", CONSENSUS_TAG)
                }
                .forEach {
                    if (it.subtract)
                        removeCounter(it.registration, it.aircraftType)
                    else
                        addCounter(it.registration, it.aircraftType)
                    writeConsensusMapToFile()
                }
        }
        socket.write(JoozdlogCommsKeywords.OK)
    }

    fun getAircraftConsensus(socket: IOWorker): Boolean =
        socket.write(AircraftTypesConsensus.getInstance().also {
            log.v("Sending ${it.consensus.size} Consensus records", CONSENSUS_TAG)
        }.toByteArray())


    /**
     * Sends current Airports to client
     * @param socket: IOWorker to take care of transmission to client
     * @return true if success, false if exception caught
     */
    fun sendAirportDb(socket: IOWorker): Boolean =
        if (AirportsStorage.fileExists)
            socket.write(AirportsStorage.airportData)
        else
            socket.write(JoozdlogCommsKeywords.SERVER_ERROR)

    /**
     * Sends current Airport DB Version to client
     * @param socket: IOWorker to take care of transmission to client
     * @return true if success, false if exception caught
     */
    fun sendAirportDbVersion(socket: IOWorker): Boolean =
        if (AirportsStorage.fileExists)
            socket.write(wrap(AirportsStorage.version))
        else
            socket.write(wrap(-1)) // -1 means SERVER_ERROR in this case

    /**
     * Changes a password.
     * @param rawLoginData: New login data, using password and email (if not empty), as ByteArray.
     *                      This will be deserialized into a [LoginData] object
     */
    fun changePassword(socket: IOWorker, flightsStorage: FlightsStorage?, rawLoginData: ByteArray): FlightsStorage? {
        if (flightsStorage?.correctKey == false) return null.also { socket.write(JoozdlogCommsKeywords.UNKNOWN_USER_OR_PASS) }
        if (flightsStorage == null) return null.also { socket.write(JoozdlogCommsKeywords.NOT_LOGGED_IN) }
        val loginData = LoginDataWithEmail.deserialize(rawLoginData)

        return try {
            UserAdministration.updatePassword(flightsStorage, loginData.password)?.also {
                socket.write(JoozdlogCommsKeywords.OK).also {
                    if (loginData.email.isNotBlank()) EmailFunctions.sendLoginLinkEmail(loginData.copy(userName = flightsStorage.loginData.userName))
                    log.n("Password changed for ${flightsStorage.loginData.userName}", CHANGE_PASSWORD_TAG)
                }
            }
        } catch (e: IOException) {
            log.e("changePassword failed: ${e.message}", CHANGE_PASSWORD_TAG)
            null
        }
    }

    /**
     * TODO Maybe make this so it cna only be tried every 10 minutes or so to prevent people flooding with emails?
     * Sets email for user and sends a confirmation email
     */
    fun setEmailForUser(socket: IOWorker, rawLoginDataWithEmail: ByteArray){
        val loginData = LoginDataWithEmail.deserialize(rawLoginDataWithEmail)
        log.v("requested to set mail for ${loginData.userName} to ${loginData.email}")
        if (UserAdministration.checkLoginValid(loginData)){
            val hashData = EmailRepository.createEmailDataForUser(loginData.userName, loginData.email)
            when(EmailFunctions.sendEmailConfirmationMail(loginData, hashData)){
                FunctionResult.SUCCESS ->
                    socket.write(JoozdlogCommsKeywords.OK).also{
                        log.n("Confirmation mail sent for user ${loginData.userName}", "backup")
                    }
                FunctionResult.BAD_EMAIL_ADDRESS -> socket.sendError(JoozdlogCommsKeywords.NOT_A_VALID_EMAIL_ADDRESS)
                else -> socket.sendError(JoozdlogCommsKeywords.SERVER_ERROR)
            }
        } else socket.sendError(JoozdlogCommsKeywords.UNKNOWN_USER_OR_PASS).also{
            log.n("User ${loginData.userName} tried to set email but gave invalid login info", "setEmailForUser")
        }
    }

    /**
     * attempt to set email to "confirmed" for user. Expects a wrapped string with [username:hashAsBase64]]
     */
    fun confirmEmail(socket: IOWorker, extraData: ByteArray){
        try{
            unwrapString(extraData).split(":").let{
                when(EmailRepository.tryToConfirmEmail(it.first(), Base64.getDecoder().decode(it.last()))){
                    true -> socket.ok()
                    false -> socket.sendError(JoozdlogCommsKeywords.EMAIL_NOT_KNOWN_OR_VERIFIED)
                    null -> socket.sendError(JoozdlogCommsKeywords.UNKNOWN_USER_OR_PASS)
                }
            }
        } catch (e: java.lang.Exception) { socket.sendError(JoozdlogCommsKeywords.BAD_DATA_RECEIVED)}
    }


    fun sendLoginLinkEmail(socket: IOWorker, rawLoginDataWithEmail: ByteArray) {
        val loginData = LoginDataWithEmail.deserialize(rawLoginDataWithEmail)
        when (val result = EmailFunctions.sendLoginLinkEmail(loginData)){
            FunctionResult.SUCCESS -> {
                socket.ok()
                log.v("Sent login link to user ${loginData.userName}", "sendLoginLinkEmail")
            }
            FunctionResult.BAD_EMAIL_ADDRESS -> {
                socket.sendError(JoozdlogCommsKeywords.EMAIL_NOT_KNOWN_OR_VERIFIED)
                log.w("Emailaddress \"${loginData.email}\" provided for user ${loginData.userName} did not match stored hash.", "sendLoginLinkEmail")
            }
            else -> log.e("Invalid response from EmailFunctions.sendLoginLinkEmail(): $result")
        }
    }


    /**
     * Send a backup mail to user
     * @param rawLoginData: [LoginDataWithEmail]
     * flightsStorage can be null, in that case login from loginData will be used.
     */
    fun sendBackupMail(socket: IOWorker, flightsStorage: FlightsStorage?, rawLoginData: ByteArray){
        log.d("SendBackupMail started!")
        val loginData = try{
            LoginDataWithEmail.deserialize(rawLoginData)
        } catch(e: Exception){
            socket.sendError(JoozdlogCommsKeywords.BAD_DATA_RECEIVED)
            return
        }
        val fs = flightsStorage ?: FlightsStorage(loginData)
        if (!fs.correctKey)  {
            socket.sendError(JoozdlogCommsKeywords.UNKNOWN_USER_OR_PASS)
            log.e("Bad login data", "backup")
            return
        }
        when (EmailFunctions.sendBackupMail(fs, loginData.email)){
            FunctionResult.SUCCESS ->
                socket.write(JoozdlogCommsKeywords.OK).also{
                    log.n("Backup mail sent for user ${flightsStorage?.loginData?.userName}", "backup")
                }

            FunctionResult.BAD_EMAIL_ADDRESS -> socket.sendError(JoozdlogCommsKeywords.NOT_A_VALID_EMAIL_ADDRESS)

            else -> socket.sendError(JoozdlogCommsKeywords.SERVER_ERROR)
        }
    }

    /**
     * Send a test email
     */
    fun sendTestEmail(socket: IOWorker){
        when (EmailFunctions.sendTestEmail()){
            FunctionResult.SUCCESS -> socket.write(JoozdlogCommsKeywords.OK)
            else -> socket.sendError(JoozdlogCommsKeywords.SERVER_ERROR)
        }
    }

    /**
     * Send checksum for flights in [flightsStorage] to [socket]
     * can also send NOT_LOGGED_IN, or SERVER_ERROR if no file found.
     */
    fun sendFlightsListChecksum(socket: IOWorker, flightsStorage: FlightsStorage?){
        if (flightsStorage?.correctKey != true) socket.sendError (JoozdlogCommsKeywords.NOT_LOGGED_IN)
        else flightsStorage.flightsFile?.let{
            socket.sendSerializable(FlightsListChecksum(it.flights))
        } ?: socket.write(JoozdlogCommsKeywords.SERVER_ERROR).also{
            log.e("server error in sendFlightsListChecksum() for user ${flightsStorage.loginData.userName} to ${socket.otherAddress} - flightsStorage.flightsFile == null")
        }
    }

    /**
     * Send list of IDs with timestamps for flights in [flightsStorage] to [socket]
     * can also send NOT_LOGGED_IN, or SERVER_ERROR if no file found.
     */
    fun sendIDsWithTimestampsList(socket: IOWorker, flightsStorage: FlightsStorage?) {
        if (flightsStorage?.correctKey != true) socket.sendError(JoozdlogCommsKeywords.NOT_LOGGED_IN)
        else flightsStorage.flightsFile?.let {
            socket.sendSerializable(it.flights.map { f -> IDWithTimeStamp(f) })
        } ?: socket.write(JoozdlogCommsKeywords.SERVER_ERROR).also{ log.e("server error in sendIDsWithTimestampsList() for user ${flightsStorage.loginData.userName} to ${socket.otherAddress} - flightsStorage.flightsFile == null") }
    }

    /**
     * Send list of IDs with timestamps for flights in [flightsStorage] to [socket]
     * can also send NOT_LOGGED_IN, or SERVER_ERROR if no file found.
     */
    fun sendFlights(socket: IOWorker, flightsStorage: FlightsStorage?, extraData: ByteArray) {
        if (flightsStorage?.correctKey != true) socket.sendError(JoozdlogCommsKeywords.NOT_LOGGED_IN)
        else flightsStorage.flightsFile?.let {
            getIDsFromExtraData(extraData)?.let { idsToSend ->
                val flightsToSend = it.flights.filter { f -> f.flightID in idsToSend }
                socket.sendSerializable(flightsToSend.map { f -> IDWithTimeStamp(f) })
            } ?: socket.sendError(JoozdlogCommsKeywords.BAD_DATA_RECEIVED).also{ log.w("Received bad data in sendFlights() - first ${maxOf(20, extraData.size)} bytes are ${extraData.take(20)}; expected a wrapped list of Ints") }
        } ?: socket.sendError(JoozdlogCommsKeywords.SERVER_ERROR).also{ log.e("server error in sendFlights() for user ${flightsStorage.loginData.userName} to ${socket.otherAddress} - flightsStorage.flightsFile == null") }
    }

    fun receiveFlights(socket: IOWorker, flightsStorage: FlightsStorage?, extraData: ByteArray) {
        if (flightsStorage?.correctKey != true) socket.sendError(JoozdlogCommsKeywords.NOT_LOGGED_IN)
        else flightsStorage.flightsFile?.let {
            getflightsFromExtraData(extraData)?.let{ newFlights ->
                flightsStorage.addFlights(newFlights)?.let {
                    socket.write(JoozdlogCommsKeywords.OK)
                    log.v("received $it flights from client", "receiveFlights")
                } ?: socket.sendError(JoozdlogCommsKeywords.SERVER_ERROR).also{ log.e("error while adding flights for user ${flightsStorage.loginData.userName} from client ${socket.otherAddress}", "receiveFlights") }
            } ?: socket.sendError(JoozdlogCommsKeywords.BAD_DATA_RECEIVED).also { log.w("Received bad data - first ${maxOf(20, extraData.size)} bytes are ${extraData.take(20)}; expected a packed list of BasicFlights", "receiveFlights") }
        }?: socket.sendError(JoozdlogCommsKeywords.SERVER_ERROR).also{ log.e("server error for user ${flightsStorage.loginData.userName} to ${socket.otherAddress} - flightsStorage.flightsFile == null", "receiveFlights") }
    }

    private fun getIDsFromExtraData(extraData: ByteArray): List<Int>? = try{
        unwrapList(extraData)
    } catch(e: Exception){
        null
    }

    private fun getflightsFromExtraData(extraData: ByteArray): List<BasicFlight>? = try{
        unpackSerialized(extraData){ BasicFlight.deserialize(it)}
    } catch(e: Exception){
        null
    }

    private fun deserializeFeedbackData(extraData: ByteArray): FeedbackData? = try{
        FeedbackData.deserialize(extraData)
    } catch(e: Exception) { null }


    private fun IOWorker.ok() = write(JoozdlogCommsKeywords.OK)

    private fun IOWorker.sendSerializable(serializable: List<JoozdSerializable>) =
        write(packSerializable(serializable))

    private fun IOWorker.sendSerializable(serializable: JoozdSerializable) =
        write(serializable.serialize())


}



