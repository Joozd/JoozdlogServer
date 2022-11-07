package extensions

import nl.joozd.comms.IOWorker
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsResponses
import utils.Logger


fun IOWorker.sendError(error: JoozdlogCommsResponses){
    Logger.singleton.v("Error: ${error.keyword} sent to ${this.otherAddress}")
    write(error.keyword)
}