package app.fitnova

import app.fitnova.domain.SleepEntry
import app.fitnova.domain.hm
import app.fitnova.domain.manualEntry
import app.fitnova.domain.sleepProblem
import app.fitnova.domain.sleepStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SleepTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 7) // a Saturday

    /** A sleep that ends on [wakeDay]; a bedtime at or after the wake time on the clock means the evening before. */
    private fun night(wakeDay: LocalDate, bed: LocalTime, wake: LocalTime): SleepEntry {
        val bedDay = if (bed >= wake) wakeDay.minusDays(1) else wakeDay
        return SleepEntry("$wakeDay-$bed", bedDay.atTime(bed).toInstant(zone).toEpochMilli(), wakeDay.atTime(wake).toInstant(zone).toEpochMilli())
    }

    @Test fun nightAcrossMidnightCountsOnTheDayYouWokeUp() {
        val s = sleepStats(listOf(night(today, LocalTime.of(23, 30), LocalTime.of(7, 0))), 480, today, zone)
        assertEquals(450, s.lastNight?.minutes)
        assertEquals(today to 450, s.week.last())
        assertEquals(0, s.week[5].second) // not on the evening it began
        assertEquals(today.minusDays(6), s.week.first().first)
    }

    @Test fun averagesAndDebtCountLoggedDaysOnly() {
        val s = sleepStats(
            listOf(
                night(today, LocalTime.of(23, 0), LocalTime.of(6, 0)), // 420: 60 short
                night(today.minusDays(1), LocalTime.of(22, 0), LocalTime.of(7, 0)), // 540: 60 over, pays back
                night(today.minusDays(3), LocalTime.of(0, 0), LocalTime.of(6, 0)), // 360: 120 short
                night(today.minusDays(10), LocalTime.of(20, 0), LocalTime.of(5, 0)), // outside the week
            ),
            480,
            today,
            zone,
        )
        assertEquals(listOf(0, 0, 0, 360, 0, 540, 420), s.week.map { it.second })
        assertEquals(440, s.avgMinutes)
        assertEquals(120, s.debtMinutes)
        assertEquals(23 * 60, s.avgBedMinuteOfDay) // 23:00, 22:00 and 00:00
        assertEquals(49, s.consistency) // ±60 around 23:00: sqrt(2400)
    }

    @Test fun oversleepingNeverMakesDebtNegative() {
        val s = sleepStats(listOf(night(today, LocalTime.of(21, 0), LocalTime.of(8, 0))), 480, today, zone)
        assertEquals(0, s.debtMinutes)
        assertNull(s.consistency) // needs three nights
    }

    @Test fun bedtimesEitherSideOfMidnightAverageToMidnight() {
        val s = sleepStats(
            listOf(
                night(today, LocalTime.of(23, 30), LocalTime.of(7, 0)),
                night(today.minusDays(1), LocalTime.of(0, 30), LocalTime.of(7, 0)),
                night(today.minusDays(2), LocalTime.of(0, 0), LocalTime.of(7, 0)),
            ),
            480,
            today,
            zone,
        )
        assertEquals(0, s.avgBedMinuteOfDay) // not 08:00, the plain average of 1410, 30 and 0
        assertEquals(24, s.consistency) // -30, +30 and 0: sqrt(600)
    }

    @Test fun napsAddToTheDayButLastNightIsTheMainSleep() {
        val main = night(today, LocalTime.of(23, 0), LocalTime.of(6, 0))
        val nap = night(today, LocalTime.of(14, 0), LocalTime.of(15, 0))
        val s = sleepStats(listOf(nap, main), 480, today, zone)
        assertEquals(main, s.lastNight)
        assertEquals(480, s.week.last().second)
        assertEquals(23 * 60, s.avgBedMinuteOfDay) // the nap's bedtime is ignored
    }

    @Test fun emptyLogHasNoAverages() {
        val s = sleepStats(emptyList(), 480, today, zone)
        assertNull(s.lastNight)
        assertNull(s.avgMinutes)
        assertNull(s.avgBedMinuteOfDay)
        assertEquals(0, s.debtMinutes)
        assertEquals(7, s.week.size)
    }

    @Test fun springForwardNightIsMeasuredInRealTime() {
        // Europe/London jumps from 01:00 to 02:00 on 29 March 2026: 23:00 to 07:00 on the clock is 7 hours.
        val london = ZoneId.of("Europe/London")
        val wakeDay = LocalDate.of(2026, 3, 29)
        val e = SleepEntry(
            "dst",
            ZonedDateTime.of(wakeDay.minusDays(1), LocalTime.of(23, 0), london).toInstant().toEpochMilli(),
            ZonedDateTime.of(wakeDay, LocalTime.of(7, 0), london).toInstant().toEpochMilli(),
        )
        assertEquals(420, e.minutes)
        val s = sleepStats(listOf(e), 480, wakeDay, london)
        assertEquals(420, s.lastNight?.minutes)
        assertEquals(23 * 60, s.avgBedMinuteOfDay)
    }

    private fun ms(day: LocalDate, time: LocalTime) = day.atTime(time).toInstant(zone).toEpochMilli()

    @Test fun manualBedtimeAfterWakeTimeMeansTheEveningBefore() {
        val (bed, wake) = manualEntry(today, LocalTime.of(23, 30), LocalTime.of(7, 0), zone)
        assertEquals(ms(today.minusDays(1), LocalTime.of(23, 30)), bed)
        assertEquals(ms(today, LocalTime.of(7, 0)), wake)
        val (napStart, napEnd) = manualEntry(today, LocalTime.of(1, 0), LocalTime.of(9, 0), zone) // after midnight, and naps
        assertEquals(ms(today, LocalTime.of(1, 0)), napStart)
        assertEquals(8 * 60L, (napEnd - napStart) / 60_000)
    }

    @Test fun manualNightOverTheSpringForwardIsSevenHours() {
        val london = ZoneId.of("Europe/London")
        val (bed, wake) = manualEntry(LocalDate.of(2026, 3, 29), LocalTime.of(23, 0), LocalTime.of(7, 0), london)
        assertEquals(7 * 60L, (wake - bed) / 60_000)
    }

    @Test fun manualNightsAreChecked() {
        val now = ms(today, LocalTime.of(12, 0))
        val logged = night(today, LocalTime.of(23, 0), LocalTime.of(7, 0))
        fun problem(bed: LocalTime, wake: LocalTime, day: LocalDate = today.minusDays(1), id: String? = null): String? {
            val (b, w) = manualEntry(day, bed, wake, zone)
            return sleepProblem(b, w, now, listOf(logged), id)
        }
        assertNull(problem(LocalTime.of(22, 30), LocalTime.of(6, 30)))
        assertEquals("Wake time is in the future", problem(LocalTime.of(23, 0), LocalTime.of(13, 0), day = today, id = logged.id))
        assertEquals("Shorter than 15 min", problem(LocalTime.of(14, 0), LocalTime.of(14, 10)))
        assertEquals("Longer than 16 h", problem(LocalTime.of(7, 0), LocalTime.of(7, 0))) // same clock time = 24 h
        assertEquals("Overlaps another night", problem(LocalTime.of(5, 0), LocalTime.of(9, 0), day = today))
        assertNull(problem(LocalTime.of(23, 30), LocalTime.of(7, 30), day = today, id = logged.id)) // editing itself
    }

    @Test fun durationsReadAsHoursAndMinutes() {
        assertEquals("45m", hm(45))
        assertEquals("7h 05m", hm(425))
        assertEquals("8h 00m", hm(480))
    }
}
