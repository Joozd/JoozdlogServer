package utils

import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


/**
 * My own logger.
 * Takes two logfiles i it's constructor, logs to them. Also logs to console if that is set.
 * if [verboseLogFile] is provided, this will always log verbose logs, even when level set to normal
 */
class Logger(
    var verboseLogFile: File? = null,
    var logFile: File? = null,
    var outputToConsole: Boolean = false,
    var level: Int = NORMAL,
    var addTimestamp: Boolean = false,
    var timestampFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    var showErrors: Boolean = true,
    var showWarnings: Boolean = true

) {
    private val timeStamp: String
        get() = if (addTimestamp) LocalDateTime.now(ZoneOffset.UTC).format(timestampFormat) else ""

    /**
     * Logs a message as NORMAL unless [messageLevel] is set
     */
    fun log(message: String, messageLevel: Int = NORMAL){
        when(messageLevel){
            NORMAL -> n(message)
            DEBUG -> d(message)
            VERBOSE -> v(message)
        }
    }

    /**
     * Log something that is normally logged (logged on all levels except NOTHING)
     */
    fun n(message: String) {
        if (level >= NORMAL) {
            cOut("N/$timeStamp - $message")
            //TODO make this log to normal logfile and verbose logfile
        }
    }

    /**
     * Log something that is logged when debugging
     * This will only end up in logfile when level is DEBUG.
     * Will show in console if level is DEBUG or higher
     */
    fun d(message: String) {
        if (level >= DEBUG)
            cOut("D/$timeStamp - $message")
        if (level == DEBUG) {
        //TODO make this log to normal logfile and verbose logfile
        }
    }

    /**
     * Log something that is logged when verbose (includes NORMAL, DEBUG and VERBOSE)
     */
    fun v(message: String) {
        verboseLogFile?.let{
            // log to verbose file no matter what
        }
        if (level >= VERBOSE)
            cOut("V/$timeStamp - $message")
            // verbose logs to normal logFile if this is set
    }

    fun e(message: String) {
        if (showErrors) {
            cOut("E/$timeStamp - $message")
            // log to normal and verbose
        }
    }

    fun newLine(){
        if (level != NOTHING){
            cOut("\n")
        }
    }

    private fun cOut(text: String) {
        if (outputToConsole) println(text)
    }


    companion object{
        const val NOTHING = 0
        const val NORMAL = 1
        const val DEBUG = 2
        const val VERBOSE = 3 // verbose is always logged to verbose file if that is provided

        //singleton implementation
        val singleton: Logger = Logger()

    }


}