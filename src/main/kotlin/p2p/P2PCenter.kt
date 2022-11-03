package p2p

import extensions.toEpochMicros
import utils.CoroutineTimerTask
import utils.Logger
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/*
 * Singleton that keeps track of all P2P sessions.
 * A "session" is basically a temporary storage for data sent by one client, to be retrieved by another.
 * Sessions are identified by their creation time and purged after at least MAX_SESSION_TIME milliseconds.
 */
object P2PCenter {
    private const val MAX_SESSION_TIME_MINUTES = 60

    private val log = Logger.singleton

    private val sessions = HashMap<Long, P2PSession>()

    fun createSession(): Long{
        log.d("createSession()")
        val ts = generateSessionTimestamp()
        log.d("Generated ID: $ts")
        sessions[ts] = P2PSession()
        log.d("Session ${sessions[ts]} made")
        purgeOnTimeout(ts)
        return ts
    }

    operator fun set(id: Long, data: ByteArray){
        sessions[id]!!.put(data)
    }

    operator fun get(id: Long) = sessions[id]?.get()

    // makes a unique timestamp, max 1 session per microsecond supported.
    private fun generateSessionTimestamp(): Long{
        var ts = Instant.now().toEpochMicros()
        while(sessions[ts] != null)
            ts++

        return ts
    }

    // removes P2PSession stored in sessions[timestamp] from sessions after MAX_SESSION_TIME seconds,
    // allowing it to be garbage collected.
    private fun purgeOnTimeout(timestamp: Long){
        CoroutineTimerTask(delay = MAX_SESSION_TIME_MINUTES.minutes){
            sessions.remove(timestamp)
        }
    }
}