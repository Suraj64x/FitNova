package app.fitnova.domain

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * A wake-up alarm that stops once you do [reps] of [exercise].
 * [days] is a bitmask: bit 0 = Monday … bit 6 = Sunday; 0 = rings once.
 */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val enabled: Boolean = true,
    val exercise: Exercise = Exercise.SQUAT,
    val reps: Int = 15,
    val label: String = "",
)

/**
 * The first ring strictly after [after], in its zone; null when the alarm is off.
 * Worked out from the local date and clock time, so DST never shifts it: a time that a spring-forward gap skips
 * rings just after the gap (01:30 becomes 02:30), and a time repeated by a fall-back rings once, at the first one.
 */
fun Alarm.next(after: ZonedDateTime): ZonedDateTime? {
    if (!enabled) return null
    val time = LocalTime.of(hour, minute)
    // Eight days: an alarm set only for today's weekday, once its time has passed, rings a week later.
    for (i in 0L..7L) {
        val date = after.toLocalDate().plusDays(i)
        if (days != 0 && (days and (1 shl (date.dayOfWeek.value - 1))) == 0) continue
        val at = ZonedDateTime.of(date, time, after.zone)
        if (at.isAfter(after)) return at
    }
    return null
}
