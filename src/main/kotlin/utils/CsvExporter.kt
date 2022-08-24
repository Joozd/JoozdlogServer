package utils

import nl.joozd.joozdlogcommon.BasicFlight

/**
 * Used to export a list of BAsicFlights to CSV.
 * Not in JoozdLogCommon because base64 function requires Android API O
 */
class CsvExporter(private val basicFlights: List<BasicFlight>) {
    private val csvString: String by lazy{
        (listOf(FIRST_LINE_V5)  +
                basicFlights.map{ it.toCsv() }).joinToString("\n")
    }

    fun toByteArray() = csvString.toByteArray(Charsets.UTF_8)

    companion object{
        const val FIRST_LINE_V5 = "flightID;Origin;dest;timeOut;timeIn;correctedTotalTime;multiPilotTime;nightTime;ifrTime;simTime;aircraftType;registration;name;name2;takeOffDay;takeOffNight;landingDay;landingNight;autoLand;flightNumber;remarks;isPIC;isPICUS;isCoPilot;isDual;isInstructor;isSim;isPF;isPlanned;autoFill;augmentedCrew;signature"
    }
}