package utils


import extensions.minus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import settings.Settings
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


/**
 * My own logger.
 * Takes two logfiles i it's constructor, logs to them. Also logs to console if that is set.
 * if [verboseLogFile] is provided, this will always log verbose logs, even when level set to normal
 */
@Suppress("unused")
class Logger(
    var verboseLogFile: File? = null,
    var logFile: File? = null,
    var debugLogFile: File = File("../log/debug.log"),
    var outputToConsole: Boolean = false,
    var level: Int = DEBUG,
    var addTimestamp: Boolean = false,
    var timestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd-kk:mm:ss.SSS"),
    var showErrors: Boolean = false,
    var showWarnings: Boolean = true
): CoroutineScope {
    override val coroutineContext = Dispatchers.IO

    private val crashLogFile = File("../log/crashes.log")

    private val timeStamp: String
        get() = if (addTimestamp) "${LocalDateTime.now(ZoneOffset.UTC).format(timestampFormat)} - " else ""

    private var mostRecentTagVerbose: String? = null
    private var mostRecentTimeStampVerbose: LocalDateTime? = LocalDateTime.of(2000, 1, 1, 0, 0) // any date in the past will do as it is only here to check if a few seconds have passed before repeating tag
    private var mostRecentTagNormal: String? = null
    private var mostRecentTimeStampNormal: LocalDateTime? = LocalDateTime.of(2000, 1, 1, 0, 0) // any date in the past will do as it is only here to check if a few seconds have passed before repeating tag


    private val tagLock = Mutex()
    private val lineLock = Mutex()

    /**
     * Logs a message as NORMAL unless [messageLevel] is set
     */
    fun log(message: String, messageLevel: Int = NORMAL, tag: String? = null){
        when(messageLevel){
            NORMAL -> n(message, tag)
            DEBUG -> d(message)
            VERBOSE -> v(message, tag)
        }
    }

    /**
     * Log something that is normally logged (logged on all levels except NOTHING)
     */
    fun n(message: String, tag: String? = null) {
        launch {
            lineLock.withLock {
                if (level >= NORMAL) {
                    verboseLogFile?.appendText("N/$timeStamp${message.addTag(tag)}\n")
                    logFile?.appendText("N/$timeStamp${message.addTag(tag)}\n")
                        ?: cOut("N/$timeStamp${message.addTag(tag)}\n")
                }
            }
        }
    }

    /**
     * Log something that is logged when debugging
     * This will end up in debug logfile
     * Will never end up in normal logfile.
     * Debug messages do not have a tag.
     */
    fun d(message: String) {
        launch {
            lineLock.withLock {
                debugLogFile.appendText("D/$timeStamp$message\n")
            }
        }
    }

    /**
     * Log something that is logged when verbose (includes NORMAL, DEBUG and VERBOSE)
     */
    fun v(message: String, tag: String? = null) {
        launch {
            lineLock.withLock {
                verboseLogFile?.appendText("V/$timeStamp${message.addTag(tag)}\n")
                if (level >= VERBOSE)
                    logFile?.appendText("V/$timeStamp${message.addTag(tag)}\n")
                        ?: cOut("V/$timeStamp${message.addTag(tag)}\n")
            }
        }
    }

    /**
     * Log a warning
     */
    fun w(message: String, tag: String? = null) {
        launch {
            lineLock.withLock {
                verboseLogFile?.appendText("W/$timeStamp${message.addTag(tag)}\n")
                if (showErrors)
                    logFile?.appendText("W/$timeStamp${message.addTag(tag)}\n")
                        ?: cOut("W/$timeStamp${message.addTag(tag)}\n")
            }
        }
    }

    /**
     * Log an error
     */
    fun e(message: String, tag: String? = null) {
        launch {
            lineLock.withLock {
                verboseLogFile?.appendText("E/$timeStamp${message.addTag(tag, force = true)}\n")
                if (showErrors) {
                    logFile?.appendText("E/$timeStamp${message.addTag(tag, force = true)}\n") ?: cOut(
                        "E/$timeStamp${
                            message.addTag(
                                tag
                            )
                        }\n"
                    )
                }
            }
        }
    }

    fun c(crashMessage: String){
        launch{
            crashLogFile.appendText(timeStamp + crashMessage + "\n***********************************************************\n")
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

    /**
     * Adds a tag to a string if [tag] is not null
     * If [tag] is the same as [mostRecentTagVerbose] it is replaced by spaces for easier readabiliy
     */
    private suspend fun String.addTag(tag: String?, force: Boolean = false, type: Int = VERBOSE): String {
        tagLock.withLock {
            val mrt = when (type){
                NORMAL -> mostRecentTagNormal
                else -> mostRecentTagVerbose
            }
            val mrtTime = when (type){
                NORMAL -> mostRecentTimeStampNormal
                else -> mostRecentTimeStampVerbose
            }
            val nonDuplicateTag: String? = when {
                tag == null -> null

                force || tag != mrt || LocalDateTime.now(ZoneOffset.UTC) - mrtTime!! > Duration.ofSeconds(10) -> {
                    if (type == NORMAL) mostRecentTagNormal = tag else mostRecentTagVerbose = tag
                    if (type == NORMAL) mostRecentTimeStampNormal = LocalDateTime.now(ZoneOffset.UTC) else mostRecentTimeStampVerbose = LocalDateTime.now(ZoneOffset.UTC)
                    tag
                }
                else -> " ".repeat(tag.length)
            }
            return listOfNotNull(nonDuplicateTag, this).joinToString(": ")
        }
    }



    companion object{
        const val NOTHING = 0
        const val NORMAL = 1
        const val VERBOSE = 2 // verbose is always logged to verbose file if that is provided
        const val DEBUG = 3

        //singleton implementation
        val singleton: Logger = Logger(verboseLogFile = File(Settings["verboseLogFile"] ?: "verbose_fallback.log"),logFile =  File(Settings["logFile"] ?: "logfile_fallback.log"))

    }



}