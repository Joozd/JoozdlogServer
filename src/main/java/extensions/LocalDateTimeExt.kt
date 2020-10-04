package extensions

import java.time.Duration
import java.time.LocalDateTime

operator fun LocalDateTime.minus(other: LocalDateTime): Duration = Duration.between(other, this)