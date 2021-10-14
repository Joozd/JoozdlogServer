package storage

import nl.joozd.serializing.SIZE_WRAPPED_INT
import nl.joozd.serializing.unwrapInt
import settings.Settings
import java.io.File

object AirportsStorage {
    private val file = File(Settings["airportsFile"]!!)
    // file = wrap<Int>(version) + packSerialized(List<Basicairport>.map {it.serialize()}

    val fileExists: Boolean
        get() = file.exists()

    val version: Int = file.inputStream().use { unwrapInt( it.readNBytes(SIZE_WRAPPED_INT) ) }
    val airportData: ByteArray = file.inputStream().use {
        it.skip(SIZE_WRAPPED_INT.toLong())
        it.readAllBytes()
    }
}