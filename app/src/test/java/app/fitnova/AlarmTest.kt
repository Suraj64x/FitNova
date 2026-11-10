package app.fitnova

import app.fitnova.domain.Alarm
import app.fitnova.domain.next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

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
}
