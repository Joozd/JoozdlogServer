package server

import aircraft.AircraftTypesConsensus
import extensions.sendError
import nl.joozd.joozdlogcommon.AircraftType
import nl.joozd.joozdlogcommon.ConsensusData
import nl.joozd.joozdlogcommon.LoginDataWithEmail
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsKeywords
import nl.joozd.joozdlogcommon.serializing.packSerialized
import nl.joozd.joozdlogcommon.serializing.toByteArray
import nl.joozd.joozdlogcommon.serializing.unpackSerialized
import nl.joozd.joozdlogcommon.serializing.wrap
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import settings.Settings
import storage.AirportsStorage
import storage.FlightsStorage
import storage.UserAdministration
import utils.CsvExporter
import utils.Logger
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
                    if (loginData.email.isNotBlank()) sendLoginLinkEmail(loginData.copy(userName = flightsStorage.loginData.userName))
                    log.n("Password changed for ${flightsStorage.loginData.userName}", CHANGE_PASSWORD_TAG)
                }
            }
        } catch (e: IOException) {
            log.e("changePassword failed: ${e.message}", CHANGE_PASSWORD_TAG)
            null
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

        // We now have a valid flightfile and a valid loginData. Exporting file and mailing it: (emailAddress checked in mailer)
        fs.flightsFile?.flights?.let {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
            val date = LocalDateTime.now().format(formatter)
            if (sendMail(
                content = "Hallon dit moet nog beter worden. Met HTML enzo. Maar voor nu: je backup csv dinges.\n\nVeel plezier!\nXOXO Joozd!",
                subject = "Joozdlog Backup CSV",
                to = loginData.email,
                attachment = CsvExporter(it).toByteArray(),
                attachmentName = "JoozdlogBackup - $date.csv",
                attachmentType = "text/csv"
            )) socket.write(JoozdlogCommsKeywords.OK).also{
                log.n("Backup mail sent for user ${flightsStorage?.loginData?.userName}", "backup")
            }
            else socket.sendError(JoozdlogCommsKeywords.NOT_A_VALID_EMAIL_ADDRESS)
        } ?: socket.sendError(JoozdlogCommsKeywords.SERVER_ERROR)
    }

    fun sendTestEmail(socket: IOWorker){
        log.n("Sending test email!")
        sendMail("HALLON TEST AMIL AUB GRGR", "Joozdlog Test email", "joozd@joozd.nl")
        socket.write(JoozdlogCommsKeywords.OK)
    }

    /**
     * Send a login link email.
     * //TODO get mail from a file (html) and own address and subject from [Settings]
     */
    fun sendLoginLinkEmail(loginData: LoginDataWithEmail): Boolean{
        log.d("Sending login email...")
        if (!seemsToBeAnEmail(loginData.email)) return false
        else {
            val passwordString = Base64.getEncoder().encodeToString(loginData.password)
            val loginLink = "https://joozdlog.joozd.nl/inject-key/${loginData.userName}:$passwordString"
            val email = EmailBuilder.startingBlank()
                .from("Joozdlog Login Link", Settings["emailFrom"]!!)
                .to(loginData.email)
                .withSubject(Settings["emailSubject"])
                .withPlainText("Hallon dit moet nog beter worden. Met HTML enzo. Maar voor nu: Je login link!\n\n$loginLink\n\nVeel plezier!\nXOXO Joozd!")
                .buildEmail()

            log.d("email built: $email, ${email.plainText}")
            MailerBuilder
                .withSMTPServer("smtp03.hostnet.nl", 587, Settings["emailFrom"]!!, Settings["noReply"])
                .withTransportStrategy(TransportStrategy.SMTP_TLS)
                .buildMailer()
                .sendMail(email)
            return true
        }
    }

    private fun sendMail(content: String, subject: String, to: String, attachment: ByteArray, attachmentName: String, attachmentType: String, fromName: String = "JoozdLog Airline Pilots\' Logbook"): Boolean{
        if (!seemsToBeAnEmail(to)) return false

        val email = EmailBuilder.startingBlank()
            .from(fromName, Settings["emailFrom"]!!)
            .to(to)
            .withSubject(subject)
            .withPlainText(content)
            .withAttachment(attachmentName, attachment, attachmentType)
            .buildEmail()

        log.d("email built: $email, ${email.plainText}")
        MailerBuilder
            .withSMTPServer("smtp03.hostnet.nl", 587, Settings["emailFrom"]!!, Settings["noReply"])
            .withTransportStrategy(TransportStrategy.SMTP_TLS)
            .buildMailer()
            .sendMail(email)
        return true
    }

    private fun sendMail(content: String, subject: String, to: String, fromName: String = "JoozdLog Airline Pilots\' Logbook"): Boolean{
        if (!seemsToBeAnEmail(to)) return false

        val email = EmailBuilder.startingBlank()
            .from(fromName, Settings["emailFrom"]!!)
            .to(to)
            .withSubject(subject)
            .withPlainText(content)
            .buildEmail()

        log.d("email built: $email, ${email.plainText}")
        MailerBuilder
            .withSMTPServer("smtp03.hostnet.nl", 587, Settings["emailFrom"]!!, Settings["noReply"])
            .withTransportStrategy(TransportStrategy.SMTP_TLS)
            .buildMailer()
            .sendMail(email)
        return true
    }


    private fun seemsToBeAnEmail(text: String) = text matches (
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                "\\@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+").toRegex()


}



