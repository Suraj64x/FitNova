package app.fitnova.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Sleeps shorter or longer than this are mistakes (a stray tap, a forgotten "I'm awake"), not nights. */
const val MIN_SLEEP_MINUTES = 15
const val MAX_SLEEP_MINUTES = 16 * 60

/** "7h 05m", or "45m" under an hour. */
fun hm(minutes: Int): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

/**
 * Epoch ms of a night entered as clock times, ending on [wakeDate]. A bedtime at or after the wake time on the
 * clock means the evening before (23:30 → 07:00); an earlier one is the same day (01:00 → 09:00, naps).
 */
fun manualEntry(wakeDate: LocalDate, bed: LocalTime, wake: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val bedDate = if (bed >= wake) wakeDate.minusDays(1) else wakeDate
    return ZonedDateTime.of(bedDate, bed, zone).toInstant().toEpochMilli() to ZonedDateTime.of(wakeDate, wake, zone).toInstant().toEpochMilli()
}

/** Why a night from [bedAt] to [wakeAt] can't be saved next to [others] (itself excluded by [id]); null when it can. */
fun sleepProblem(bedAt: Long, wakeAt: Long, now: Long, others: List<SleepEntry>, id: String? = null): String? {
    val minutes = (wakeAt - bedAt) / 60_000
    return when {
        wakeAt > now + 5 * 60_000 -> "Wake time is in the future"
        minutes < MIN_SLEEP_MINUTES -> "Shorter than $MIN_SLEEP_MINUTES min"
        minutes > MAX_SLEEP_MINUTES -> "Longer than ${MAX_SLEEP_MINUTES / 60} h"
        others.any { it.id != id && it.bedAt < wakeAt && it.wakeAt > bedAt } -> "Overlaps another night"
        else -> null
    }
}

/**
 * One sleep, from going to bed to waking up (epoch ms). Storing instants makes a night that crosses midnight
 * or a DST change plain subtraction. [source] is "tap" (bed / awake buttons), "alarm" (alarm challenge) or "manual".
 */
data class SleepEntry(val id: String, val bedAt: Long, val wakeAt: Long, val source: String = "tap") {
    val minutes get() = ((wakeAt - bedAt) / 60_000).toInt()
}

data class SleepStats(
    /** The longest sleep that ended today; null until one is logged. */
    val lastNight: SleepEntry?,
    /** Minutes slept on each of the last 7 days, oldest first, ending today. A sleep counts on the day you woke up. */
    val week: List<Pair<LocalDate, Int>>,
    /** Average over the days in [week] that have a log; null when none do. */
    val avgMinutes: Int?,
    /** Average bedtime of each day's main sleep, as minutes after midnight (0..1439); null when none. */
    val avgBedMinuteOfDay: Int?,
    /** How far those bedtimes spread, in minutes (standard deviation, lower is steadier); null below 3 nights. */
    val consistency: Int?,
    /** Minutes short of the goal over the logged days; oversleeping pays some back. Never below 0. */
    val debtMinutes: Int,
)

private const val DAY_MINUTES = 24 * 60

fun sleepStats(entries: List<SleepEntry>, goalMinutes: Int, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): SleepStats {
    val start = today.minusDays(6)
    val byDay = entries.filter { it.minutes > 0 }
        .groupBy { Instant.ofEpochMilli(it.wakeAt).atZone(zone).toLocalDate() }
        .filterKeys { !it.isBefore(start) && !it.isAfter(today) }
    val week = (0L..6L).map { start.plusDays(it) }.map { d -> d to byDay[d].orEmpty().sumOf { it.minutes } }
    val logged = week.map { it.second }.filter { it > 0 }

    // Bedtimes as minutes after noon, so 23:30 and 00:30 are 60 apart (not 1380) and average to midnight.
    // ponytail: the noon anchor breaks for people who sleep around noon (night shifts); use a circular mean then.
    val beds = byDay.values.map { day ->
        val t = Instant.ofEpochMilli(day.maxBy { it.minutes }.bedAt).atZone(zone).toLocalTime()
        (t.hour * 60 + t.minute + DAY_MINUTES / 2) % DAY_MINUTES
    }
    val mean = beds.takeIf { it.isNotEmpty() }?.average()

    return SleepStats(
        lastNight = byDay[today]?.maxBy { it.minutes },
        week = week,
        avgMinutes = logged.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        avgBedMinuteOfDay = mean?.let { (it.roundToInt() + DAY_MINUTES / 2) % DAY_MINUTES },
        consistency = if (mean == null || beds.size < 3) null else sqrt(beds.sumOf { (it - mean) * (it - mean) } / beds.size).roundToInt(),
        debtMinutes = maxOf(0, logged.sumOf { goalMinutes - it }),
    )
}
