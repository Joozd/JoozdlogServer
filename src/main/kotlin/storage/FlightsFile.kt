package storage

import crypto.AESCrypto
import nl.joozd.joozdlogcommon.BasicFlight
import nl.joozd.joozdlogcommon.serializing.toByteArray
import nl.joozd.joozdlogcommon.serializing.packSerialized

/**
 * This holds flights with some metadata:
 * @param timestamp: Timestamp of latest change
 * @param flights: List of all of user's flights
 */
data class FlightsFile(var timestamp: Long, var flights: List<BasicFlight>) {
    // Version of BasicFlight used (only used for retrieval from disk)
    private val version
        get() = BasicFlight.VERSION.version
    fun toByteArray(): ByteArray = version.toByteArray() + timestamp.toByteArray() + packSerialized(flights.map{it.serialize()})

    /**
     * Makes a bytearray of:
     * - version    +
     * - timestamp  +
     * - all encrypted flights
     */
    fun toEncryptedByteArray(key: ByteArray): ByteArray?{
        val rawdata = packSerialized(flights.map{it.serialize()})
        val encrypted = AESCrypto.encrypt(rawdata, key)
        encrypted?.let {enc ->
            return version.toByteArray() + timestamp.toByteArray() + enc
        }
        return null
    }
    override fun toString() = "FlightsFile v. $version - timestamp $timestamp - ${flights.size} flights."
}