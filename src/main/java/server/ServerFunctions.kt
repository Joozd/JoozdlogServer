package server

import aircraft.AircraftTypesConsensus
import nl.joozd.joozdlogcommon.AircraftType
import nl.joozd.joozdlogcommon.ConsensusData
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsKeywords
import nl.joozd.joozdlogcommon.serializing.packSerialized
import nl.joozd.joozdlogcommon.serializing.toByteArray
import nl.joozd.joozdlogcommon.serializing.unpackSerialized
import nl.joozd.joozdlogcommon.serializing.wrap
import storage.AirportsStorage
import storage.FlightsStorage
import storage.UserAdministration
import utils.Logger
import java.io.IOException
import java.time.Instant


object ServerFunctions {
    /**
     * Sends current time as to client as seconds since epoch
     * @param socket: IOWorker to take care of transmission to client
     * @return true if success, false if exception caught
     */
    fun sendTimestamp(socket: IOWorker): Boolean =
        socket.write(Instant.now().epochSecond.toByteArray())

    /**
     * Sends current AircraftTypesConsensus.aircraftTypes to client
     * @param socket: IOWorker to take care of transmission to client
     * @return true if success, false if exception caught
     */
    fun sendAircraftTypes(socket: IOWorker): Boolean {
        val acTypes: List<AircraftType> = AircraftTypesConsensus.getInstance().aircraftTypes
        val payload = packSerialized(acTypes.map{it.serialize()})
        return socket.write(payload)
    }

    fun sendAircraftTypesVersion(socket: IOWorker): Boolean {
        val version: Int = AircraftTypesConsensus.getInstance().aircraftTypesVersion
        val payload = wrap(version)
        return socket.write(payload)
    }
    fun sendForcedTypesVersion(socket: IOWorker): Boolean {
        val version: Int = AircraftTypesConsensus.getInstance().forcedTypesVersion
        val payload = wrap(version)
        return socket.write(payload)
    }
    fun sendForcedTypes(socket: IOWorker): Boolean {
        val payload = AircraftTypesConsensus.getInstance().serializedForcedTypes
        return socket.write(payload)
    }



    /**
     * Add or remove consensus data to or from a registration
     */
    fun addCToConsensus(socket: IOWorker, extraData: ByteArray){
        with (AircraftTypesConsensus.getInstance()) {
            unpackSerialized(extraData).map { ConsensusData.deserialize(it) }.forEach {
                if (it.subtract)
                    removeCounter(it.registration, it.aircraftType)
                else
                    addCounter(it.registration, it.aircraftType)
                writeConsensusMapToFile()
            }
        }
        socket.write(JoozdlogCommsKeywords.OK)
    }

    fun getAircraftConsensus(socket: IOWorker): Boolean =
         socket.write(AircraftTypesConsensus.getInstance().toByteArray())





    /**
     * Sends current Airports to client
     * @param socket: IOWorker to take care of transmission to client
     * @return true if success, false if exception caught
     */
    fun sendAirportDb(socket: IOWorker): Boolean =
        if (AirportsStorage.fileExists)
            socket.write(AirportsStorage.airportData)
        else
            socket.write(JoozdlogCommsKeywords.SERVER_ERROR)

    /**
     * Sends current Airport DB Version to client
     * @param socket: IOWorker to take care of transmission to client
     * @return true if success, false if exception caught
     */
    fun sendAirportDbVersion(socket: IOWorker): Boolean =
        if (AirportsStorage.fileExists)
            socket.write(wrap(AirportsStorage.version))
        else
            socket.write(wrap(-1)) // -1 means SERVER_ERROR in this case

    fun changePassword(socket: IOWorker, flightsStorage: FlightsStorage?, newKey: ByteArray): FlightsStorage? {
        if (flightsStorage?.correctKey == false) return socket.write(JoozdlogCommsKeywords.UNKNOWN_USER_OR_PASS).run{null}
        if (flightsStorage == null) return socket.write(JoozdlogCommsKeywords.NOT_LOGGED_IN).run{null}
        return try {
            UserAdministration.updatePassword(flightsStorage, newKey)?.also{
                socket.write(JoozdlogCommsKeywords.OK)
            }
        } catch (e: IOException){
            Logger.singleton.e("changePassword failed: ${e.message}")
            null
        }
    }

}