package server.serverFunctions

import data.feedback.FeedbackHandler
import extensions.sendError
import nl.joozd.comms.IOWorker
import nl.joozd.joozdlogcommon.*
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsResponses
import nl.joozd.serializing.*
import p2p.P2PCenter
import data.email.EmailRepository
import utils.Logger

object ServerFunctions {
    private val log get() = Logger.singleton

    fun receiveFeedback(socket: IOWorker, extraData: ByteArray){
        deserializeFeedbackData(extraData)?.let { feedbackData ->
            FeedbackHandler.instance.addFeedback(feedbackData)
            socket.ok()
        } ?: socket.sendError(JoozdlogCommsResponses.BAD_DATA_RECEIVED).also { log.w("Bad data received from ${socket.otherAddress}", "receiveFeedback")}
    }

    /**
     * attempt to set email to "confirmed" for user. Expects a wrapped string with [username:hashAsBase64]]
     */
    fun confirmEmail(socket: IOWorker, extraData: ByteArray){
        try{
            val confirmationString: String = unwrap(extraData)
            when(EmailRepository.confirmEmail(confirmationString)){
                true -> socket.ok()
                false -> socket.sendError(JoozdlogCommsResponses.EMAIL_NOT_KNOWN_OR_VERIFIED)
                null -> socket.sendError(JoozdlogCommsResponses.ID_NOT_FOUND) // server error means ID not found on server
            }
        } catch (e: Exception) { socket.sendError(JoozdlogCommsResponses.BAD_DATA_RECEIVED)}
    }

    fun storeP2PData(ioWorker: IOWorker, extraData: ByteArray){
        log.d("StoreP2PData started")
        val sessionID = P2PCenter.createSession()
        log.d("Started P2P session $sessionID")
        P2PCenter[sessionID] = extraData
        ioWorker.write(wrap(sessionID))
    }

    fun getP2PData(ioWorker: IOWorker, extraData: ByteArray){
        val sessionID: Long = unwrap(extraData)
        val data = try{
            P2PCenter[sessionID]
        } catch (e: Exception){
            null
        }
        if (data != null)
            ioWorker.write(data)
        else ioWorker.sendError(JoozdlogCommsResponses.ID_NOT_FOUND)
    }

    // Extradata is expected to be [nl.joozd.joozdlogcommon.EmailData]
    // Client expects a wrapped Long as response (the Email ID)
    // User expects to get a confirmation email.
    fun setEmail(ioWorker: IOWorker, extraData: ByteArray){
        val emailData = try{ EmailData.deserialize(extraData) }
        catch (e: Exception){
            ioWorker.sendError(JoozdlogCommsResponses.BAD_DATA_RECEIVED)
            return
        }
        val newEmailRecord = EmailRepository.registerNewEmail(emailData.emailAddress)
        EmailFunctions.sendEmailConfirmationMail(newEmailRecord, emailData.emailAddress)
        ioWorker.write(wrap(newEmailRecord.id))
    }

    // Extradata is expected to be LoginDataWithEmail, with a random (unused) key.
    // Client expects a wrapped Long as response (the Email ID)
    fun migrateEmail(ioWorker: IOWorker, extraData: ByteArray){
        //Deprecated class here for migrating
        @Suppress("DEPRECATION") val loginData = LoginDataWithEmail.deserialize(extraData)
        val newEmailRecord = EmailRepository.migrateEmail(loginData) ?: return ioWorker.sendError(JoozdlogCommsResponses.ID_NOT_FOUND)
        ioWorker.write(wrap(newEmailRecord.id))
    }

    fun forwardBackupEmail(ioWorker: IOWorker, extraData: ByteArray){
        val backupEmailData = EmailData.deserialize(extraData)
        if (EmailFunctions.forwardBackupEmail(backupEmailData))
            ioWorker.ok()
        else ioWorker.sendError (JoozdlogCommsResponses.EMAIL_NOT_KNOWN_OR_VERIFIED)

    }

    private fun deserializeFeedbackData(extraData: ByteArray): FeedbackData? = try{
        FeedbackData.deserialize(extraData)
    } catch(e: Exception) { null }

    private fun IOWorker.ok() = write(JoozdlogCommsResponses.OK.keyword)
}



