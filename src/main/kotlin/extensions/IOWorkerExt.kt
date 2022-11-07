package extensions

import nl.joozd.comms.IOWorker
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsResponses
import utils.Logger


fun IOWorker.sendError(error: JoozdlogCommsResponses, extraDataForLogger: String? = null){
    var logMessage = "Error: ${error.keyword} sent to ${this.otherAddress}"
    extraDataForLogger?.let{
        logMessage += "\n$it"
    }
    Logger.singleton.v(logMessage)
    write(error.keyword)
}