package server

import settings.Settings
import utils.Logger
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import javax.net.ServerSocketFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory


/* ClassFileServer.java -- a simple file server that can server
 * Http get request in both clear and secure channel
 *
 * The ClassFileServer implements a ClassServer that
 * reads files from the file system. See the
 * doc for the "Main" method for how to run this
 * server.
 */

/* ClassFileServer.java -- a simple file server that can server
 * Http get request in both clear and secure channel
 *
 * The ClassFileServer implements a ClassServer that
 * reads files from the file system. See the
 * doc for the "Main" method for how to run this
 * server.
 */

/**
 * Constructs a ClassFileServer.
 *
 *
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
            Logger.singleton.e("Error in JoozdlogServer.handle():\n${err.stackTraceToString()}")
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
                            "*** Starting JoozdlogServer 0006 ***\n" +
                            "*** Logging level: ${log.level.toString().padStart(2, ' ')}            ***\n" +
                            "************************************\n")


            val port = DefaultServerPort
            val type = "TLS"
            try {
                val ss = getServerSocketFactory(type)?.createServerSocket(port) ?: error ("Unable to create socket - null error (#0001")
                JoozdlogServer(ss)
            } catch (e: IOException) {
                log.e("Unable to start ClassServer: " + e.message)
                log.e(e.stackTrace.toString())
            }
        }

        /**
         * To update keys:
         * keytool -genkeypair -alias simple-cert -keyalg RSA -keysize 2048 -keystore letsencrypt.jks -dname "CN=joozd.nl" -storepass a5yzVR0E
         * keytool -certreq -alias simple-cert -keystore letsencrypt.jks -file joozd_nl.csr -storepass a5yzVR0E -ext san=dns:joozd.nl
         * certbot certonly --manual --csr /opt/joozdlogserver/joozd_nl.csr --preferred-challenges "dns"
         * keytool -importcert -alias simple-cert -keystore letsencrypt.jks -storepass a5yzVR0E -file joozd_nl.pem
         */
        private fun getServerSocketFactory(type: String): ServerSocketFactory? {
            if (type == "TLS") {
                val ssf: SSLServerSocketFactory
                try { // set up key manager to do server authentication
                    val passphrase = Settings["ssl"]!!.toCharArray() // might as well crash if not found since won't be coming online anyway
                    val ctx: SSLContext = SSLContext.getInstance("TLS")
                    val kmf: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
                    val ks: KeyStore = KeyStore.getInstance("JKS")
                    ks.load(FileInputStream("letsencrypt.jks"), passphrase)
                    kmf.init(ks, passphrase)
                    ctx.init(kmf.keyManagers, null, null)
                    ssf = ctx.serverSocketFactory
                    return ssf
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                return ServerSocketFactory.getDefault()
            }
            return null
        }


    }

}