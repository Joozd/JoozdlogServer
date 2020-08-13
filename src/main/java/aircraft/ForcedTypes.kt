package aircraft

import nl.joozd.joozdlogcommon.AircraftType
import nl.joozd.joozdlogcommon.ForcedTypeData
import nl.joozd.joozdlogcommon.serializing.packSerialized
import java.io.File

/**
 * Reads a forcedTypesFile, and matches any found hits to it's aircraftType
 * types are placed in [forcedTypes]
 * If [version] cannot be read from file, it is null
 */
class ForcedTypes(forcedTypesFile: File, aircraftTypes: List<AircraftType>) {
    val forcedTypes: Map<String, AircraftType>
    val version: Int
    init{
        val lines = forcedTypesFile.bufferedReader().use {it.readLines() }.filter{it.isNotEmpty()}.filter{it[0] != '#'}
        val headerLines = lines.filter{it.startsWith("!")}.map{it.drop(1).trim()}
        val dataLines = lines.filter{!it.startsWith("!")}.map{it.trim()}

        //read header lines (right now only version, but easily extendable like this
        val headers = mapOf<String, String>().toMutableMap()
        headerLines.forEach { headerLine -> // a headerLine is a string "!version 1"
            val words = headerLine.split(" ")
            headers[words[0]] = words.drop(1).joinToString(" ").trim()
        }
        version = headers["version"]?.toInt() ?: -1.also{println ("headers[version] == null / headers: $headers")}

        //read regs + types
        val foundTypes = mapOf<String, AircraftType?>().toMutableMap()
        val shortTypeNames = aircraftTypes.map{it.shortName}
        dataLines.forEach {dataLine ->
            val data = dataLine.split(",").map{it.trim()}
            foundTypes[data[0]] = aircraftTypes.firstOrNull { it.shortName == data[1] }
        }
        forcedTypes = foundTypes.filter { it.value != null }.mapValues { it.value!! }
        if (forcedTypes.size != foundTypes.size){
            println("Not all lines found, no matches for ${foundTypes.filter {it.value == null}.keys.toList()}")
        }
    }

    /**
     * Returns a serialized list of ForcedTypeData
     */
    fun toByteArray(): ByteArray =
        packSerialized(forcedTypes.map{ ForcedTypeData(it.key, it.value.name).serialize() })
}