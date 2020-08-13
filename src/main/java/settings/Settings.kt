package settings

import java.io.File

object Settings {
    private const val settingsFileName = "joozdlogsettings"
    private val settings = makeSettingsMap(File(settingsFileName))

    /****************************************************************************************************************
     * put JoozdlogServerSettings below here
     ****************************************************************************************************************/

    val logFile: String by JoozdlogServerSettings(settings, "ERROR")
    val verboseLogFile: String by JoozdlogServerSettings(settings, "ERROR")
    val logLevel: Int by JoozdlogServerSettings(settings, 4)

    val aircraftFile: String by JoozdlogServerSettings(settings, "ERROR")
    val consensusFile: String by JoozdlogServerSettings(settings, "ERROR")
    val forcedTypesFile: String by JoozdlogServerSettings(settings, "ERROR")

    /****************************************************************************************************************
     * put JoozdlogServerSettings above here
     ****************************************************************************************************************/


    private fun makeSettingsMap(settingsFile: File): Map<String, String>
    {
        val lines = settingsFile.bufferedReader().use {it.readLines() }.filter{it.isNotEmpty()}.filter{it[0] != '#'}
        val headerLines = lines.filter{it.startsWith("!")}.map{it.drop(1).trim()}
        val dataLines = lines.filter{!it.startsWith("!")}.map{it.trim()}

        val headers = mapOf<String, String>().toMutableMap()
        headerLines.forEach { headerLine -> // a headerLine is a string "!version 1"
            val words = headerLine.split(" ")
            headers[words[0]] = words.drop(1).joinToString(" ").trim()
        }
        return headers
    }
}