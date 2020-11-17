package settings

import java.io.File

//TODO watch settings file for changes

object Settings {
    private const val settingsFileName = "joozdlogsettings"
    private val settings = makeSettingsMap(File(settingsFileName))

    /****************************************************************************************************************
     * put JoozdlogServerSettings below here
     ****************************************************************************************************************/

    val logFile: String by JoozdlogServerSettings(settings, "ERROR")
    val verboseLogFile: String by JoozdlogServerSettings(settings, "ERROR")
    val logLevel: Int by JoozdlogServerSettings(settings, 2)
    val maxMessageSize: Int by JoozdlogServerSettings(settings, -1)
    val minimumConsensus: Double by JoozdlogServerSettings(settings, 0.0)

    /****************************************************************************************************************
     * put JoozdlogServerSettings above here
     ****************************************************************************************************************/


    private fun makeSettingsMap(settingsFile: File): Map<String, String>
    {
        val lines = settingsFile.bufferedReader().use {it.readLines() }.filter{it.isNotEmpty()}.filter{it[0] != '#'}
        val headerLines = lines.filter{it.startsWith("!")}.map{it.drop(1).trim()}

        val headers = mapOf<String, String>().toMutableMap()
        headerLines.forEach { headerLine -> // a headerLine is a string "!version 1"
            val words = headerLine.split(" ")
            headers[words[0]] = words.drop(1).joinToString(" ").trim()
        }
        return headers
    }

    /**
     * Directly get a string value from [settings]
     * @return <String> value of that setting, or null if not found
     */
    operator fun get(value: String): String?{
        return settings[value]
    }
}