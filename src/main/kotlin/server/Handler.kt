package server

import extensions.sendError
import nl.joozd.comms.IOWorker
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsRequests.*
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsRequests
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsResponses
import nl.joozd.serializing.*
import server.serverFunctions.ServerFunctions
import settings.Settings
import utils.Logger
import java.io.Closeable
/**
 * Handler should get an open IOWorker upon construction
 * It will use this socket to do all kinds of things.
 * Handler should be closed after usage, which will close the IOWorker
 * @param ioWorker: an open IOWorker to be used for communications
 */

class Handler(private val ioWorker: IOWorker): Closeable {
    private val log get() = Logger.singleton

    fun handleAll(){
        var keepGoing = true

        while (keepGoing) {
            try {
                val receivedMessage = ioWorker.read(Settings.maxMessageSize)
                if (nextType(receivedMessage) == STRING) {                       // check if package seems to start with a string
                    val request = unwrapString(nextWrap(receivedMessage))
                    val extraData = receivedMessage.sliceArray(wrap(request).size until receivedMessage.size)
                    log.d("DEBUG: received request: $request from ${ioWorker.otherAddress}")
                    when (JoozdlogCommsRequests.toKeyword(request)) { // keep this in same order as JoozdlogComsKeywords pls
                        HELLO -> {
                            // not used at the moment, but useful when updating protocol
                            // Currently this is HELLO_V1, in case protocol 2 gets used, another HELLO will be given.
                        }

                        // Extradata is expected to be [nl.joozd.joozdlogcommon.EmailData]
                        // Client expects a wrapped Long as response (the Email ID)
                        // User expects to get a confirmation email.
                        SET_EMAIL -> ServerFunctions.setEmail(ioWorker, extraData)

                        // Extradata is expected to be LoginDataWithEmail, with a random (unused) key.
                        // Client expects a wrapped Long as response (the Email ID)
                        MIGRATE_EMAIL_DATA -> ServerFunctions.migrateEmail(ioWorker, extraData)

                        // Extradata is expected to be [nl.joozd.joozdlogcommon.EmailData] with a CSV as ByteArray in its attachment.
                        SENDING_BACKUP_EMAIL_DATA -> ServerFunctions.forwardBackupEmail(ioWorker, extraData)

                        // Extradata is expected to be a wrapped string as in [EmailDataRecord.confirmationString()]
                        CONFIRM_EMAIL -> ServerFunctions.confirmEmail(ioWorker, extraData)

                        // Extradata is expected to be [nl.joozd.joozdlogcommon.FeedbackData]
                        SENDING_FEEDBACK -> ServerFunctions.receiveFeedback(ioWorker, extraData)

                        // Stores extraData in a p2p session, does not touch that data in any other way
                        // Client expects a wrapped Long as response (the P2P session ID)
                        SENDING_P2P_DATA -> ServerFunctions.storeP2PData(ioWorker, extraData)

                        // Extradata is expected to be a session ID (wrapped Long) as extraData
                        // Client expects a bytearray stored in the P2P session with that ID
                        REQUEST_P2P_DATA -> ServerFunctions.getP2PData(ioWorker, extraData)

                        END_OF_SESSION -> {
                            keepGoing = false
                            log.d("Closing connection.\n")
                            ioWorker.close()
                        }

                        // unknown or bad request will close connection
                        UNKNOWN_KEYWORD -> {
                            log.v("unknown or bad request: $request from ${ioWorker.otherAddress}, closing connection")
                            keepGoing = false
                        }
                    }

                } else {
                    log.n("Invalid request from ${ioWorker.otherAddress}, stopping handleAll()", "Handler")
                    keepGoing = false
                }
            }
            catch (e: Exception){
                log.w("Caught exception $e")
                log.c("Error in JoozdlogServer.handle():\n${e.stackTraceToString()}")
                ioWorker.sendError(JoozdlogCommsResponses.SERVER_ERROR)
            }
        }
    }

    override fun close() = ioWorker.close()
}