package extensions

import java.time.Instant
import java.time.temporal.ChronoUnit

fun Instant.toEpochMicros(): Long =
    ChronoUnit.MICROS.between(Instant.EPOCH, this)