package data.aircraft

import nl.joozd.joozdlogcommon.AircraftType
import nl.joozd.serializing.packSerialized
import java.io.File

/**
 * Takes a File containing a list of Aircraft as found at
 * https://raw.githubusercontent.com/jpatokal/openflights/master/data/planes.dat
 * ie. a list of lines containing aircraft in the format
 * "Name","Short_code","code","IFR/VFR", "MP", "ME"
 * `"Embraer 190","E90","E190","IFR","MP","ME"`
 *
 * @param fileIn: The File object containing said file
 * @return a byteArray containing packed serialized AircraftType objects
 */

class AircraftTypesFromFile(file: File) {
    companion object{
        const val MULTI_PILOT = "\"MP\""
        const val MULTI_ENGINE = "\"ME\""
    }
    val aircraftTypes: List<AircraftType>
    val version: Int

    init {
        // remove empty and commented lines
        val lines = file.bufferedReader().use {it.readLines() }.filter{it.isNotEmpty()}.filter{it[0] != '#'}
        version = lines.first{it.startsWith("!version")}.split(' ').last().toInt()

        //only try to make aircraft from lines starting with `"`
        aircraftTypes = lines.filter{it[0] == '\"'}.map{line -> aircraftTypeFromString(line)}
    }




    fun toByteArray() =
        packSerialized(aircraftTypes.map { it.serialize() })

    override fun toString() = "AircraftTypesFromFile: ${aircraftTypes.size} AircraftTypes"


    /**
     * Expects format:
     * "Embraer 190","E90","E190","IFR","MP","ME"
     * One of those three can be `\N` if not available. In that case, use the other one twice.
     */
    private fun aircraftTypeFromString(string: String): AircraftType {
        val nameIgnoreTypeMpMe = string.split(",")
        val name = nameIgnoreTypeMpMe[0].trim().filter { it != '\"' }
        val type =
            (if (nameIgnoreTypeMpMe[2].trim() == "\\N") nameIgnoreTypeMpMe[1].trim() else nameIgnoreTypeMpMe[2].trim()).filter { it != '\"' }
        val multiPilot = nameIgnoreTypeMpMe[4].trim() == MULTI_PILOT
        val multiEngine = nameIgnoreTypeMpMe[5].trim() == MULTI_ENGINE
        return AircraftType(
            name,
            type,
            multiPilot,
            multiEngine
        )
    }
}
