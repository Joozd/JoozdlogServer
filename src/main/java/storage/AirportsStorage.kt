package storage

import nl.joozd.joozdlogcommon.BasicAirport
import nl.joozd.joozdlogcommon.legacy.basicairport.BasicAirport_version1
import nl.joozd.joozdlogcommon.serializing.SIZE_WRAPPED_INT
import nl.joozd.joozdlogcommon.serializing.unpackSerialized
import nl.joozd.joozdlogcommon.serializing.unwrapInt
import java.io.File

object AirportsStorage {
    private val file = File("./airports")
    // file = wrap<Int>(version) + packSerialized(List<Basicairport>.map {it.serialize()}

    val fileExists: Boolean
        get() = file.exists()

    val version: Int = file.inputStream().use { unwrapInt( it.readNBytes(SIZE_WRAPPED_INT) ) }
    val airportData: ByteArray = file.inputStream().use {
        it.skip(SIZE_WRAPPED_INT.toLong())
        it.readAllBytes()
    }
}