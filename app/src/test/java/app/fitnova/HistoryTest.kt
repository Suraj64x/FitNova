package app.fitnova

import app.fitnova.domain.Exercise
import app.fitnova.domain.MonthTotals
import app.fitnova.domain.PersonalBest
import app.fitnova.domain.Workout
import app.fitnova.domain.dayTotals
import app.fitnova.domain.focusDay
import app.fitnova.domain.heat
import app.fitnova.domain.monthGrid
import app.fitnova.domain.monthTotals
import app.fitnova.domain.personalBests
import app.fitnova.domain.workoutsByDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryTest {
    private val utc = ZoneOffset.UTC

    private fun w(
        at: LocalDateTime,
        exercise: Exercise = Exercise.PUSHUP,
        reps: Int = 10,
        seconds: Int = 60,
        form: Int = 100,
        tracked: Boolean = true,
    ) = Workout("$at-${exercise.id}-$reps-$seconds", exercise, reps, seconds, form, tracked, at.toInstant(utc).toEpochMilli())

    @Test fun monthGridStartsOnMonday() {
        val nov = monthGrid(YearMonth.of(2026, 11)) // 1 Nov 2026 is a Sunday
        assertEquals(6, nov.takeWhile { it == null }.size)
        assertEquals(36, nov.size)
        assertEquals(LocalDate.of(2026, 11, 30), nov.last())

        val feb = monthGrid(YearMonth.of(2027, 2)) // starts on a Monday: no blanks
        assertEquals(LocalDate.of(2027, 2, 1), feb.first())
        assertEquals(28, feb.size)

        val leap = monthGrid(YearMonth.of(2028, 2)) // Tuesday, 29 days
        assertEquals(listOf(null, LocalDate.of(2028, 2, 1)), leap.take(2))
        assertEquals(30, leap.size)
    }

    @Test fun daysFollowTheLocalZone() {
        val late = w(LocalDateTime.of(2026, 11, 6, 20, 0)) // 20:00 UTC = 01:30 next day in India
        assertEquals(setOf(LocalDate.of(2026, 11, 6)), workoutsByDay(listOf(late), utc).keys)
        assertEquals(setOf(LocalDate.of(2026, 11, 7)), workoutsByDay(listOf(late), ZoneId.of("Asia/Kolkata")).keys)
    }

    @Test fun dayAndMonthTotals() {
        val byDay = workoutsByDay(
            listOf(
                w(LocalDateTime.of(2026, 11, 7, 9, 0), reps = 20),
                w(LocalDateTime.of(2026, 11, 7, 18, 0), reps = 10, form = 50), // 5 pts
                w(LocalDateTime.of(2026, 11, 2, 7, 0), reps = 8, tracked = false), // manual: 4 pts
                w(LocalDateTime.of(2026, 10, 31, 7, 0), reps = 30), // other month
            ),
            utc,
        )
        assertEquals(25, dayTotals(byDay)[LocalDate.of(2026, 11, 7)])
        assertEquals(MonthTotals(points = 29, workouts = 3, activeDays = 2), monthTotals(byDay, YearMonth.of(2026, 11)))
        assertEquals(MonthTotals(0, 0, 0), monthTotals(byDay, YearMonth.of(2026, 9)))
    }

    @Test fun heatStepsAgainstTheGoal() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, 4), listOf(0, 1, 12, 13, 24, 25, 49, 50, 200).map { heat(it, 50) })
        assertEquals(4, heat(1, 0)) // no goal set: any activity counts as hit
    }

    @Test fun focusDayPrefersTheLatestActiveDay() {
        val today = LocalDate.of(2026, 11, 7)
        val active = listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 2))
        assertEquals(LocalDate.of(2026, 10, 21), focusDay(YearMonth.of(2026, 10), active, today))
        assertEquals(LocalDate.of(2026, 9, 30), focusDay(YearMonth.of(2026, 9), active, today)) // nothing logged: month end
        assertEquals(LocalDate.of(2026, 11, 2), focusDay(YearMonth.of(2026, 11), active, today))
        assertEquals(today, focusDay(YearMonth.of(2026, 11), emptyList(), today)) // never a future day
    }

    @Test fun personalBestsPerExercise() {
        val day = LocalDateTime.of(2026, 11, 7, 9, 0)
        val bests = personalBests(
            listOf(
                w(day, Exercise.PUSHUP, reps = 10, form = 70),
                w(day.plusHours(1), Exercise.PUSHUP, reps = 25, form = 0, tracked = false), // manual: counts for reps, not form
                w(day.plusHours(2), Exercise.PUSHUP, reps = 20, form = 90),
                w(day, Exercise.PLANK, reps = 0, seconds = 60, form = 80),
                w(day.plusHours(3), Exercise.PLANK, reps = 0, seconds = 90, form = 0, tracked = false),
                w(day, Exercise.BURPEE, reps = 0, seconds = 5, tracked = false), // nothing done: no best
            ),
        )
        assertEquals(
            listOf(
                PersonalBest(Exercise.PUSHUP, best = 25, form = 90, workouts = 3),
                PersonalBest(Exercise.PLANK, best = 90, form = 80, workouts = 2), // holds rank by seconds
            ),
            bests,
        )
        assertNull(personalBests(listOf(w(day, Exercise.SQUAT, tracked = false, form = 0))).single().form)
    }
}
