package server

import java.io.*
import java.net.ServerSocket
import java.net.Socket


/*
 * ClassServer.java -- a simple file server that can serve
 * Http get request in both clear and secure channel
 */

/*
 * ClassServer.java -- a simple file server that can serve
 * Http get request in both clear and secure channel
 */
abstract class ClassServer protected constructor(ss: ServerSocket?) : Runnable {
    private var server: ServerSocket? = null
    /**
     * Returns an array of bytes containing the bytes for
     * the file represented by the argument **path**.
     *
     * @return message to send, or null if no reply
     * @exception FileNotFoundException if the file corresponding
     * to **path** could not be loaded.
     * @exception IOException if error occurs reading the class
     */
    @Throws(IOException::class, FileNotFoundException::class)
    abstract fun handle(socket: Socket)

    /**
     * The "listen" thread that accepts a connection to the
     * server, parses the header to obtain the file name
     * and sends back the bytes for the file (or error
     * if the file is not found or the response was malformed).
     */
    override fun run() {
        println("Listening...")
        val socket: Socket
        // accept a connection
        socket = try {
            server!!.accept()
        } catch (e: IOException) {
            println("Class Server died: " + e.message)
            e.printStackTrace()
            return
        }
        println("Incoming connection from ${socket.inetAddress.hostName}")
        // create a new thread to accept the next connection
        newListener()

        handle(socket)

    }

    /**
     * Create a new thread to listen.
     */
    private fun newListener() {
        Thread(this).start()
    }

    companion object {
        /**
         * @return input from socket         *
         */
    }

    /**
     * Constructs a ClassServer based on **ss** and
     * makes a listener thread
     */
    init {
        server = ss
        newListener()
    }
}