package extensions

import nl.joozd.comms.IOWorker

fun IOWorker.sendError(error: String){
    write(error)
}