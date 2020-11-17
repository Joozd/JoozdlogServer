package extensions

import server.IOWorker

fun IOWorker.sendError(error: String){
    write(error)
}