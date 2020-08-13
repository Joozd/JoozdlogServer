package storage

import crypto.AESCrypto
import nl.joozd.joozdlogcommon.BasicFlight
import nl.joozd.joozdlogcommon.serializing.toByteArray
import nl.joozd.joozdlogcommon.serializing.packSerialized

/**
 * This holds flights with some metadata:
 * @param version: Version of BasicFlight used (only used for retrieval from disk)
 * @param timestamp: Timestamp of latest change
 * @param flights: List of all of user's flights
 */
data class FlightsFile(var timestamp: Long, var flights: List<BasicFlight>) {
    private val version
        get() = BasicFlight.VERSION.version
    fun toByteArray(): ByteArray = version.toByteArray() + timestamp.toByteArray() + packSerialized(flights.map{it.serialize()})

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