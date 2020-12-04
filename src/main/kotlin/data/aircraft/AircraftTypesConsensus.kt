package data.aircraft

import nl.joozd.joozdlogcommon.AircraftType
import nl.joozd.joozdlogcommon.serializing.listFromBytes
import nl.joozd.joozdlogcommon.serializing.mapFromBytes
import nl.joozd.joozdlogcommon.serializing.toByteArray
import settings.Settings
import java.io.File

/**
 * theAircraftTypesWorker class provides a "black box" solution for keeping track of
 * - known airaft types
 * - Registrations consensus*
 *
 * *consensus: Registrations connected to aircraft types.
 * *If a registration is above CONSENSUS_LIMIT times connected to a type, that counts as consensus.
 * *format: Map<String, List<TypeCounter>
 *     ie. "PH-EZE" to ([E190 / 8], [E175 / 1])
 *
 * constructor:
 * @param aircraftFile: File containing aircraftTypes, to be passed to an AircraftTypesFromFile constructor
 * @param consensusFile: File for saving consensus data
 * @param forcedTypesfile: File containing checked data to be forced as consensus
 */

class AircraftTypesConsensus(private val aircraftFile: File, private val consensusFile: File, private val forcedTypesfile: File) {
    companion object{
        private const val AIRCRAFTFILE  = "aircrafttypes"
        private const val CONSENSUSFILE = "consensus"
        private const val FORCEDFILE    = "forcedtypes"
        private val aircraftDirectory = Settings["typesDir"]
        private val consensusLimit: Double = Settings.minimumConsensus

        /*
        fun createFiles(consensusFile: File, forcedTypesFile: File) {
            val consensusMap: Map<String, List<TypeCounter>> = emptyMap()
            val forcedMap: Map<String, List<TypeCounter>> = emptyMap()
            writeMapToFile(consensusMap, consensusFile)
            writeMapToFile(forcedMap, forcedTypesFile)
        }
        private fun writeMapToFile(map: Map<String, List<TypeCounter>>, file: File) {
            val regsToSerializedMap: Map<String, ByteArray> = map.mapValues{ entry -> entry.value.map {tc -> tc.serialize()}.toByteArray() }
            val bytesToWrite = regsToSerializedMap.toByteArray()
            file.writeBytes(bytesToWrite)
        }
        */
        private var INSTANCE: AircraftTypesConsensus? = null
        fun getInstance(): AircraftTypesConsensus = synchronized(this)
        {
            INSTANCE ?: run {
                INSTANCE = AircraftTypesConsensus(
                    File(aircraftDirectory + AIRCRAFTFILE),
                    File(aircraftDirectory + CONSENSUSFILE),
                    File(aircraftDirectory + FORCEDFILE)
                )
                INSTANCE!!
            }
        }
    }

    //to be private after testing
    private var _aircraftTypes = AircraftTypesFromFile(aircraftFile)
    val aircraftTypes: List<AircraftType>
        get() = _aircraftTypes.aircraftTypes
    val aircraftTypesVersion: Int
        get() = _aircraftTypes.version

    private val consensusMap: MutableMap<String, List<TypeCounter>> = getConsensusMapFromFile()

    private val _forcedTypes = ForcedTypes(forcedTypesfile, aircraftTypes)
    val forcedMap: Map<String, AircraftType>
        get() = _forcedTypes.forcedTypes
    val forcedTypesVersion = _forcedTypes.version

    /************************************************************************************************
     * Public vals & vars
     ************************************************************************************************/

    val knownRegistrations: List<String>
        get() = consensusMap.keys.toList()

    /**
     * Gives a map of all registrations for which there is consensus
     */
    val consensus: Map<String, AircraftType>
        get() = consensusMap.mapValues { entry -> checkConsensus(entry.value)  }
            .filterValues { it != null }
            .mapValues{entry -> entry.value!! }

    /**
     * Gives a serialized Map<String, ByteArray>, where the ByteArray is a serialized AircraftType
     */
    val serializedConsensus: ByteArray
        get() = consensus.mapValues {entry -> entry.value.serialize()}.toByteArray()

    val serializedForcedTypes: ByteArray
        get() = _forcedTypes.toByteArray()

    /**
     * Request consensus.
     * @param registration: String with an aircraft registration
     * @return  If no consensus (either because below threshold or because reg unknown): null
     *          else AircraftType that is supposed to match this registration
     */
    fun requestConsensus(registration: String): AircraftType? = consensusMap[registration]?.let{ tcList ->
        checkConsensus(tcList)
    }






    /**
     * In case aircraftFile is updated, use this to update aircraftTypes
     */
    fun forceAircraftTypesUpdate(){
        _aircraftTypes = AircraftTypesFromFile(aircraftFile)
    }

    /**
     * Add a typeCounter to the list with 1 count.
     * @link consolidateCountersList will take care of making the list proper
     */
    fun addCounter(registration: String, aircraftType: AircraftType){
        val knownTypes = consensusMap[registration] ?: emptyList()
        consensusMap[registration] = consolidateCountersList(knownTypes + listOf(TypeCounter(aircraftType, 1)))
    }

    /**
     * Add a typeCounter to the list with -1 count.
     * @link consolidateCountersList will take care of making the list proper
     */
    fun removeCounter(registration: String, aircraftType: AircraftType){
        val knownTypes = consensusMap[registration] ?: emptyList()
        consensusMap[registration] = consolidateCountersList(knownTypes + listOf(TypeCounter(aircraftType, -1)))
    }

    /**
     * File contains serialized consensusMap
     * File specified in constructor
     */
    private fun getConsensusMapFromFile(): MutableMap<String, List<TypeCounter>>{
        val bytes = consensusFile.readBytes()
        val regsToSerializedMap = mapFromBytes<String, ByteArray>(bytes)
        /*
         * Every entry in the map is deserialized to a List<ByteArray>. Every item in that list then to a TypeCounter
         * At this point we have a Map<String<List<TypeCounter>> which we only have to change to a MutableMap.
         */
        return regsToSerializedMap.mapValues { entry -> listFromBytes<ByteArray>(entry.value).map{stc -> TypeCounter.deserialize(stc)}}.toMutableMap()
    }

    /**
     * Serializes consensusMap and writes it to file
     */
    fun writeConsensusMapToFile() {
        /*
         * Every entry in the map is transformed so that it's `value<List<TypeCounter>` is first
         * transformed to List<ByteArray> and then that list to <ByteArray>
         * so then we have a Map<String, ByteArray> that we can serialize.
         */
        val regsToSerializedMap: Map<String, ByteArray> = consensusMap.mapValues { entry ->
            entry.value.map { tc -> tc.serialize() }.toByteArray()
        }
        val bytesToWrite = regsToSerializedMap.toByteArray()
        consensusFile.writeBytes(bytesToWrite)
    }

    /**
     * Consolidates AircraftCounters so that the counts of the same AircraftTypes are added and merged into one.
     */
    private fun consolidateCountersList(cl: List<TypeCounter>): List<TypeCounter> =
        cl.groupBy { it.type }.mapValues { entry -> TypeCounter(entry.key, entry.value.sumBy { it.count }) }.values.toList().filter{it.count > 0}

    /**
     * Checks if a list of TypeCounters has consensus. (ie. more than CONSENSUS_LIMIT part of counts go to the same type)
     * if so, returns most common AircraftType, else null
     */
    private fun checkConsensus(tcList: List<TypeCounter>): AircraftType?{
        if (tcList.isEmpty()) return null
        val totalCount = tcList.map{it.count}.sum()
        return tcList.maxBy { it.count }?.let {candidate ->
            if (candidate.count / totalCount.toDouble() > consensusLimit) candidate.type else null
        }
    }

    /**
     * @see [serializedConsensus]
     */
    fun toByteArray() = serializedConsensus
}