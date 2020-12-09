package server.serverFunctions

import data.aircraft.AircraftTypesConsensus
import extensions.sendError
import nl.joozd.joozdlogcommon.AircraftType
import nl.joozd.joozdlogcommon.ConsensusData
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsKeywords
import nl.joozd.joozdlogcommon.serializing.*
import server.IOWorker
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
     * @param newLoginData: New login data, using password and email (if not empty)
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
                    true -> socket.write(JoozdlogCommsKeywords.OK)
                    false -> socket.sendError(JoozdlogCommsKeywords.EMAIL_NOT_KNOWN_OR_VERIFIED)
                    null -> socket.sendError(JoozdlogCommsKeywords.UNKNOWN_USER_OR_PASS)
                }
            }
        } catch (e: java.lang.Exception) { socket.sendError(JoozdlogCommsKeywords.BAD_DATA_RECEIVED)}
    }


    fun sendLoginLinkEmail(rawLoginDataWithEmail: ByteArray) = EmailFunctions.sendLoginLinkEmail(LoginDataWithEmail.deserialize(rawLoginDataWithEmail))



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








}



