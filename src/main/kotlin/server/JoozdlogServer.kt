package server

import nl.joozd.comms.JoozdCommsServerSocketFactory
import nl.joozd.comms.ClassServer
import nl.joozd.comms.IOWorker
import settings.Settings
import utils.Logger
import java.io.*
import java.net.ServerSocket
import java.net.Socket

/**
 * Constructs a ClassFileServer.
 */

/**
 * To update keys:
 * keytool -genkeypair -alias simple-cert -keyalg RSA -keysize 2048 -keystore letsencrypt.jks -dname "CN=joozd.nl" -storepass XxXPASSW0RDXxX
 * keytool -certreq -alias simple-cert -keystore letsencrypt.jks -file joozd_nl.csr -storepass XxXPASSW0RDXxX -ext san=dns:joozd.nl
 * certbot certonly --manual --csr /opt/joozdlogserver/joozd_nl.csr --preferred-challenges "dns"
 * keytool -importcert -alias simple-cert -keystore letsencrypt.jks -storepass XxXPASSW0RDXxX -file joozd_nl.pem
 */

class JoozdlogServer (ss: ServerSocket?) : ClassServer(ss)
 {
    /**
     * Handle requests, this is pretty much the main function.
     * It gets a socket and gives that to Handler which will do all the work.
     */
    @Throws(IOException::class)
    override fun handle(socket: Socket) {
        try {
            val handler = Handler(IOWorker(socket))
            handler.use {
                it.handleAll()
            }
        } catch (err: Throwable){
            Logger.singleton.c("Error in JoozdlogServer.handle():\n${err.stackTraceToString()}")
        }
    }

    companion object {
        private const val DefaultServerPort = 1337

        /**
         * TODO make documentation
         */
        @JvmStatic
        fun main(args: Array<String>) {
            //Set logger settings
            val log = Logger.singleton.apply{
                logFile = File(Settings.logFile)
                verboseLogFile = File(Settings.verboseLogFile)
                outputToConsole = true
                addTimestamp = true
                level = Settings.logLevel
            }
            log.n(  "\n************************************\n" +
                            "*** Starting JoozdlogServer 0015 ***\n" +
                            "*** Logging level: ${log.level.toString().padStart(2, ' ')}            ***\n" +
                            "************************************\n")


            val port = DefaultServerPort
            try {
                val ss = JoozdCommsServerSocketFactory.getServerSocketFactory(
                    keyStoreFile = "letsencrypt.jks",
                    keyStorePass = Settings["ssl"]!!,
                    keyStoreAlgorithm = "SunX509"
                ).createServerSocket(port) ?: error ("Unable to create socket - null error (#0001)")

                JoozdlogServer(ss)
            } catch (e: IOException) {
                log.e("Unable to start ClassServer: " + e.message)
                log.e(e.stackTrace.toString())
            }
        }
    }

}