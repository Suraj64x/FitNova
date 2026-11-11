package app.fitnova.domain

import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * A wake-up alarm that stops once you do [reps] of [exercise].
 * [days] is a bitmask: bit 0 = Monday … bit 6 = Sunday; 0 = rings once.
 * [nextAt] is the moment it is armed with the system (epoch ms); null when off or not allowed to ring.
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
    val nextAt: Long? = null,
)

const val WEEKDAYS = 0b0011111
const val WEEKENDS = 0b1100000
const val EVERY_DAY = 0b1111111

/** Standing moves the camera can count: no floor work at 6 am. */
val ALARM_EXERCISES = listOf(Exercise.SQUAT, Exercise.JACK, Exercise.HIGH_KNEES, Exercise.LUNGE)

/** A wake-up sized target per move (the editor steps by 5). */
fun alarmReps(exercise: Exercise) = when (exercise) {
    Exercise.JACK, Exercise.HIGH_KNEES -> 30
    Exercise.LUNGE -> 10
    else -> 15
}

/**
 * The first [hour]:[minute] strictly after [after], in its zone, on one of [days] (0 = any day).
 * Worked out from the local date and clock time, so DST never shifts it: a time that a spring-forward gap skips
 * rings just after the gap (01:30 becomes 02:30), and a time repeated by a fall-back rings once, at the first one.
 */
fun nextTime(hour: Int, minute: Int, days: Int, after: ZonedDateTime): ZonedDateTime {
    val time = LocalTime.of(hour, minute)
    // Eight days: an alarm set only for today's weekday, once its time has passed, rings a week later.
    for (i in 0L..7L) {
        val date = after.toLocalDate().plusDays(i)
        if (days != 0 && (days and (1 shl (date.dayOfWeek.value - 1))) == 0) continue
        val at = ZonedDateTime.of(date, time, after.zone)
        if (at.isAfter(after)) return at
    }
    error("days must be 0..127, was $days")
}

/** The first ring strictly after [after], in its zone; null when the alarm is off. */
fun Alarm.next(after: ZonedDateTime): ZonedDateTime? = if (enabled) nextTime(hour, minute, days, after) else null

/** A one-time alarm whose armed moment passed without ringing (phone off, clock moved on): switch it off, never move it to tomorrow. */
fun Alarm.missed(now: Long) = enabled && days == 0 && nextAt != null && nextAt < now

/** The soonest ring among these alarms after [after], with its alarm. */
fun List<Alarm>.nextRing(after: ZonedDateTime): Pair<Alarm, ZonedDateTime>? =
    mapNotNull { a -> a.next(after)?.let { a to it } }.minByOrNull { it.second }

/** "7h 50m" up to a day, then "2d 5h". */
fun untilText(minutes: Int): String = if (minutes < 24 * 60) hm(minutes) else "${minutes / (24 * 60)}d ${minutes % (24 * 60) / 60}h"

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** How [days] repeats, for screen readers and short labels. */
fun repeatText(days: Int) = when (days) {
    0 -> "Once"
    EVERY_DAY -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    else -> DAY_NAMES.filterIndexed { i, _ -> (days and (1 shl i)) != 0 }.joinToString(", ")
}

// ---- Maths fallback: for a dark room, a covered camera or no camera permission ----

const val SUMS_TO_SOLVE = 3

data class Sum(val a: Int, val b: Int) {
    val answer get() = a + b

    companion object {
        /** Two two-digit numbers: hard enough to need a working brain, easy enough at 6 am. */
        fun random(rnd: Random = Random.Default) = Sum(rnd.nextInt(12, 90), rnd.nextInt(12, 90))
    }
}

/**
 * Keypad state for [SUMS_TO_SOLVE] sums in a row. An answer is checked as soon as it has as many digits as the
 * right one, so there is no enter key; a wrong answer swaps in a fresh sum.
 */
data class SumChallenge(val sum: Sum, val typed: String = "", val solved: Int = 0, val wrong: Boolean = false) {
    val done get() = solved >= SUMS_TO_SOLVE

    /** [key] is a digit or [BACKSPACE]; [next] makes the following sum. */
    fun press(key: Char, next: () -> Sum): SumChallenge {
        if (done) return this
        if (key == BACKSPACE) return copy(typed = typed.dropLast(1), wrong = false)
        if (!key.isDigit()) return this
        val t = typed + key
        return when {
            t.length < sum.answer.toString().length -> copy(typed = t, wrong = false)
            t.toInt() == sum.answer -> SumChallenge(next(), solved = solved + 1)
            else -> SumChallenge(next(), solved = solved, wrong = true)
        }
    }

    companion object {
        const val BACKSPACE = '<'
    }
}
