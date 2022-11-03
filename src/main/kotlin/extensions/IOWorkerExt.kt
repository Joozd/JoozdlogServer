package extensions

import nl.joozd.comms.IOWorker
import nl.joozd.joozdlogcommon.comms.JoozdlogCommsResponses

fun IOWorker.sendError(error: JoozdlogCommsResponses){
    write(error.keyword)
}