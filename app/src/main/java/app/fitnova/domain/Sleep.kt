package app.fitnova.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
