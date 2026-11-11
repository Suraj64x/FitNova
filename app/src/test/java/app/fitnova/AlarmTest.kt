package app.fitnova

import app.fitnova.domain.Alarm
import app.fitnova.domain.EVERY_DAY
import app.fitnova.domain.SUMS_TO_SOLVE
import app.fitnova.domain.Sum
import app.fitnova.domain.SumChallenge
import app.fitnova.domain.WEEKDAYS
import app.fitnova.domain.WEEKENDS
import app.fitnova.domain.missed
import app.fitnova.domain.next
import app.fitnova.domain.nextRing
import app.fitnova.domain.repeatText
import app.fitnova.domain.untilText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.random.Random

class AlarmTest {
    private val london = ZoneId.of("Europe/London") // 2026: BST from Sun 29 Mar, back to GMT on Sun 25 Oct

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, london)

    private fun alarm(hour: Int, minute: Int = 0, days: Int = 0, enabled: Boolean = true) = Alarm("a", hour, minute, days, enabled)

    @Test fun oneTimeRingsTodayWhileTheTimeIsAhead() {
        assertEquals(at(11, 7, 7, 30), alarm(7, 30).next(at(11, 7, 6, 0)))
    }

    @Test fun oneTimeRingsTomorrowOnceTheTimeHasCome() {
        assertEquals(at(11, 8, 7, 30), alarm(7, 30).next(at(11, 7, 7, 30))) // strictly after: not again this minute
        assertEquals(at(11, 8, 7, 30), alarm(7, 30).next(at(11, 7, 21, 0)))
    }

    @Test fun weekdaysSkipTheWeekend() {
        val weekdays = 0b0011111 // Monday to Friday
        assertEquals(at(11, 9, 7), alarm(7, days = weekdays).next(at(11, 6, 8))) // Friday 08:00 -> Monday
        assertEquals(at(11, 6, 7), alarm(7, days = weekdays).next(at(11, 6, 6))) // Friday 06:00 -> today
    }

    @Test fun singleDayWrapsToNextWeek() {
        assertEquals(at(11, 16, 7), alarm(7, days = 0b1).next(at(11, 9, 7, 30))) // Monday only, asked Monday after 07:00
        assertEquals(at(11, 15, 9), alarm(9, days = 1 shl 6).next(at(11, 9, 7, 30))) // Sunday only, asked on Monday
    }

    @Test fun offAlarmNeverRings() {
        assertNull(alarm(7, enabled = false).next(at(11, 7, 6)))
    }

    @Test fun timeSkippedBySpringForwardRingsJustAfterTheGap() {
        val ring = alarm(1, 30).next(at(3, 28, 22))!! // 01:30 doesn't exist on 29 March
        assertEquals(LocalDateTime.of(2026, 3, 29, 2, 30), ring.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(1), ring.offset)
    }

    @Test fun dailyAlarmKeepsItsClockTimeOverTheShortNight() {
        val ring = alarm(7, days = 0b1111111).next(at(3, 28, 23))!!
        assertEquals(at(3, 29, 7), ring)
        assertEquals(Duration.ofHours(7), Duration.between(at(3, 28, 23), ring)) // the night is an hour shorter
    }

    @Test fun timeRepeatedByFallBackRingsOnce() {
        val first = alarm(1, 30).next(at(10, 24, 22))!! // 01:30 happens twice on 25 October
        assertEquals(ZoneOffset.ofHours(1), first.offset) // the earlier one, still BST
        assertEquals(LocalDate.of(2026, 10, 26), alarm(1, 30).next(first)!!.toLocalDate()) // not again at 01:30 GMT
    }

    @Test fun everyDayAndWeekendRepeats() {
        assertEquals(at(11, 8, 7), alarm(7, days = EVERY_DAY).next(at(11, 7, 7, 1))) // Saturday 07:01 -> Sunday
        assertEquals(at(11, 7, 9), alarm(9, days = WEEKENDS).next(at(11, 6, 20))) // Friday evening -> Saturday
        assertEquals(at(11, 14, 9), alarm(9, days = WEEKENDS).next(at(11, 8, 9, 30))) // Sunday after 09:00 -> next Saturday
    }

    @Test fun midnightAlarmRingsAtTheStartOfTheNextDay() {
        assertEquals(at(11, 8, 0), alarm(0).next(at(11, 7, 23, 59)))
        assertEquals(at(11, 9, 0), alarm(0).next(at(11, 8, 0))) // exactly midnight: not again this minute
    }

    @Test fun missedOnlyForOneTimeAlarmsWhoseMomentPassed() {
        val now = at(11, 7, 8).toInstant().toEpochMilli()
        val past = at(11, 7, 7).toInstant().toEpochMilli()
        val future = at(11, 8, 7).toInstant().toEpochMilli()
        assertTrue(alarm(7).copy(nextAt = past).missed(now)) // the phone was off at 07:00
        assertFalse(alarm(7).copy(nextAt = future).missed(now))
        assertFalse(alarm(7).missed(now)) // never armed (no permission yet): arm it, don't drop it
        assertFalse(alarm(7, days = WEEKDAYS).copy(nextAt = past).missed(now)) // repeats just move on
        assertFalse(alarm(7, enabled = false).copy(nextAt = past).missed(now))
    }

    @Test fun nextRingIsTheSoonestEnabledAlarm() {
        val early = Alarm("early", 6, 0, enabled = false)
        val seven = Alarm("seven", 7, 0, days = WEEKDAYS)
        val nine = Alarm("nine", 9, 0)
        val (alarm, ring) = listOf(nine, early, seven).nextRing(at(11, 7, 5))!! // Saturday: weekdays wait for Monday
        assertEquals("nine", alarm.id)
        assertEquals(at(11, 7, 9), ring)
        assertNull(listOf(early).nextRing(at(11, 7, 5)))
    }

    @Test fun repeatTextNamesCommonPatterns() {
        assertEquals("Once", repeatText(0))
        assertEquals("Every day", repeatText(EVERY_DAY))
        assertEquals("Weekdays", repeatText(WEEKDAYS))
        assertEquals("Weekends", repeatText(WEEKENDS))
        assertEquals("Mon, Wed, Sun", repeatText(0b1000101))
    }

    @Test fun untilTextSwitchesToDaysAfterADay() {
        assertEquals("45m", untilText(45))
        assertEquals("7h 05m", untilText(7 * 60 + 5))
        assertEquals("2d 5h", untilText(2 * 24 * 60 + 5 * 60 + 59))
    }

    @Test fun sumsAreTwoTwoDigitNumbers() {
        val rnd = Random(7)
        repeat(1_000) {
            val s = Sum.random(rnd)
            assertTrue(s.a in 12..89 && s.b in 12..89)
            assertEquals(s.a + s.b, s.answer)
        }
    }

    private fun type(c: SumChallenge, keys: String, next: () -> Sum) = keys.fold(c) { acc, k -> acc.press(k, next) }

    @Test fun threeRightAnswersStopTheAlarm() {
        var c = SumChallenge(Sum(47, 38)) // 85
        c = type(c, "8") { Sum(50, 50) }
        assertEquals("8", c.typed)
        c = type(c, "5") { Sum(50, 50) } // right: the next sum, 100, needs three digits
        assertEquals(1, c.solved)
        c = type(c, "10") { error("checked before the third digit") }
        assertEquals("10", c.typed)
        c = type(c, "0") { Sum(12, 12) }
        c = type(c, "24") { Sum(30, 30) }
        assertEquals(SUMS_TO_SOLVE, c.solved)
        assertTrue(c.done)
        assertEquals(c, c.press('5') { Sum(1, 1) }) // finished: keys do nothing
    }

    @Test fun wrongAnswerSwapsInAFreshSum() {
        var c = SumChallenge(Sum(20, 20), solved = 1)
        c = type(c, "41") { Sum(33, 33) }
        assertEquals(Sum(33, 33), c.sum)
        assertEquals(1, c.solved) // no progress, no penalty
        assertTrue(c.wrong)
        assertEquals("", c.typed)
        c = c.press('6') { Sum(0, 0) }
        assertFalse(c.wrong) // the hint goes as soon as they type again
    }

    @Test fun backspaceEditsTheAnswer() {
        var c = SumChallenge(Sum(47, 38))
        c = type(c, "9") { Sum(1, 1) }
        c = c.press(SumChallenge.BACKSPACE) { Sum(1, 1) }
        assertEquals("", c.typed)
        c = c.press(SumChallenge.BACKSPACE) { Sum(1, 1) } // nothing left to delete
        assertEquals("", c.typed)
        assertEquals(Sum(47, 38), c.sum)
    }
}
