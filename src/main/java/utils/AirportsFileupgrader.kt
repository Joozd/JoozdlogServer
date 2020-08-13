package utils

import nl.joozd.joozdlogcommon.BasicAirport
import nl.joozd.joozdlogcommon.legacy.basicairport.BasicAirport_version1
import nl.joozd.joozdlogcommon.serializing.*
import java.io.File

class AirportsFileupgrader {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("hoi")
            val fileIn = File("c:\\temp\\airports")
            val fileOut = File("c:\\temp\\airportsv2")
            upgradeArsportsv1tov2(fileIn, fileOut)
        }

        fun upgradeArsportsv1tov2(fileIn: File, fileOut: File) {
            val version: Int = fileIn.inputStream().use { unwrapInt(it.readNBytes(SIZE_WRAPPED_INT)) }
            require(version == 1 && BasicAirport.VERSION.version == 2)
            val airportData: ByteArray = fileIn.inputStream().use {
                it.skip(SIZE_WRAPPED_INT.toLong())
                it.readAllBytes()
            }
            val allAirports: List<BasicAirport> =
                when (version) {
                    1 -> unpackSerialized(airportData).map { a -> BasicAirport_version1.deserialize(a) }.map {
                        BasicAirport(
                            it.id,
                            it.ident,
                            it.type,
                            it.name,
                            it.latitude_deg,
                            it.longitude_deg,
                            it.elevation_ft,
                            it.municipality,
                            it.iata_code
                        )
                    }
                    2 -> unpackSerialized(airportData).map { a -> BasicAirport.deserialize(a) }
                    else -> error("airport file has unknown version (${version})")
                }

            val serialized = packSerialized(allAirports.map { it.serialize() })
            val v = wrap(BasicAirport.VERSION.version)
            println (v.toList())
            fileOut.writeBytes(v + serialized)
        }
    }



}