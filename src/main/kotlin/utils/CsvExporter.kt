package utils

import nl.joozd.joozdlogcommon.BasicFlight
import java.time.Instant
import java.util.*

/**
 * Used to export a list of BAsicFlights to CSV.
 * Not in JoozdLogCommon because base64 function requires Android API O
 */
class CsvExporter(private val basicFlights: List<BasicFlight>) {
    val csvString: String by lazy{
        (listOf(FIRST_LINE_V5)  +
                basicFlights.map{ it.toCsvV5() }).joinToString("\n")
    }

    fun toByteArray() = csvString.toByteArray(Charsets.UTF_8)


    private fun BasicFlight.toCsvV5(): String {
        return listOf<String>(
            flightID.toString(),
            orig,
            dest,
            Instant.ofEpochSecond(timeOut).toString(),// from original Flight
            Instant.ofEpochSecond(timeIn).toString(), // from original Flight
            correctedTotalTime.toString(),
            nightTime.toString(),
            ifrTime.toString(),
            simTime.toString(),
            aircraft,
            registration,
            name,
            name2,
            takeOffDay.toString(),
            takeOffNight.toString(),
            landingDay.toString(),
            landingNight.toString(),
            autoLand.toString(),
            flightNumber,
            remarks,
            isPIC.toString(),
            isPICUS.toString(),
            isCoPilot.toString(),
            isDual.toString(),
            isInstructor.toString(),
            isSim.toString(),
            isPF.toString(),
            isPlanned.toString(),
            // unknownToServer.toString(),
            autoFill.toString(),
            augmentedCrew.toString(),
            // DELETEFLAG,
            // timeStamp,
            Base64.getEncoder().encodeToString(signature.toByteArray(Charsets.UTF_8))
        ).joinToString(";") { it.replace(';', '|') }
    }

    companion object{
        const val FIRST_LINE_V5 = "flightID;Origin;dest;timeOut;timeIn;correctedTotalTime;multiPilotTime;nightTime;ifrTime;simTime;aircraftType;registration;name;name2;takeOffDay;takeOffNight;landingDay;landingNight;autoLand;flightNumber;remarks;isPIC;isPICUS;isCoPilot;isDual;isInstructor;isSim;isPF;isPlanned;autoFill;augmentedCrew;signature"
    }
}