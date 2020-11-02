package server

import data.Identifiers
import nl.joozd.joozdlogcommon.comms.Packet
import nl.joozd.joozdlogcommon.serializing.intFromBytes
import settings.Settings

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.net.Socket

/**
 * IOWorker will simplify reading/writing from/to a socket.
 * It should get an open socket upon construction which it will use
 * It should be closed after usage, which will close the socket.
 * @param socket: An open socket to be used for communications
 */
//TODO this is stopped mid-coding so needs thorough checking
class IOWorker(private val socket: Socket): Closeable {
    private val outputStream = socket.getOutputStream()
    private val inputStream = BufferedInputStream(socket.getInputStream())
    val clientAddress = socket.inetAddress.hostName

    /**
     * returns the next input from client
     */
    fun read(): ByteArray = getInput(inputStream)


    /**
     * Writes a packet to client
     */
    fun write(data: Packet): Boolean {
        try {
            outputStream.write(data.content)
            outputStream.flush()
        } catch (ie: IOException) {
            ie.printStackTrace()
            return false
        }
        return true
    }

    /**
     * write can be used with strings or collections of bytes
     * in this case it will make a packet and write that
     */
    fun write(data: ByteArray) = write(Packet(data))
    fun write(data: String) = write(Packet(data).also{println("sending ${it.message.take(40)}")})
    fun write(data: List<Byte>) = write(Packet(data))


    override fun close() {
        socket.close()
    }

    @Throws(IOException::class)
    private fun getInput(inputStream: BufferedInputStream): ByteArray {
        val buffer = ByteArray(8192)
        val header = ByteArray(Identifiers.HEADER.size + 4)

        //Read the header as it comes in, or fail trying.
        repeat(header.size) {
            val r = inputStream.read()
            if (r < 0) throw IOException("Stream too short: ${it - 1} bytes")
            header[it] = r.toByte()
        }
        val expectedSize = intFromBytes(header.takeLast(4))
        if (expectedSize > Settings.maxMessageSize) throw IOException("size bigger than ${Settings.maxMessageSize}")
        val message = mutableListOf<Byte>()

        //read buffers until correct amount of bytes reached or fail trying
        while (message.size < expectedSize) {
            val b = inputStream.read(buffer)
            if (b < 0) throw IOException("Stream too short: expect $expectedSize, got ${message.size}")
            message.addAll(buffer.take(b))
        }
        return message.toByteArray()
    }
}