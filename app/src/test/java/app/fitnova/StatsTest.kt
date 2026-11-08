package app.fitnova

import app.fitnova.domain.Activity
import app.fitnova.domain.Body
import app.fitnova.domain.Exercise
import app.fitnova.domain.Goal
import app.fitnova.domain.Sex
import app.fitnova.domain.Workout
import app.fitnova.domain.points
import app.fitnova.domain.stats
import app.fitnova.domain.targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

class StatsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 7) // a Saturday

    private fun on(day: LocalDate, reps: Int = 10, form: Int = 100, exercise: Exercise = Exercise.PUSHUP, tracked: Boolean = true) = Workout(
        id = "$day-$reps-${exercise.id}",
        exercise = exercise,
        reps = reps,
        seconds = 60,
        form = form,
        tracked = tracked,
        startedAt = day.atTime(9, 0).toInstant(zone).toEpochMilli(),
    )

    @Test fun pointsMatchServerFormula() {
        assertEquals(10, points(10, 60, 100, tracked = true))
        assertEquals(8, points(10, 60, 80, tracked = true))
        assertEquals(4, points(7, 9, 0, tracked = false)) // 3.5 rounds half up, like Postgres round()
        assertEquals(30, points(0, 60, 100, tracked = true)) // holds: 1 point per 2 seconds
    }

    @Test fun everyExerciseHasAUniqueId() {
        assertEquals(Exercise.entries.size, Exercise.entries.map { it.id }.toSet().size)
        assertTrue(Exercise.entries.all { it.id.matches(Regex("^[a-z_]{2,32}$")) }) // server check constraint
    }

    @Test fun streakCountsConsecutiveDays() {
        val s = stats(listOf(on(today), on(today.minusDays(1)), on(today.minusDays(3))), today, zone)
        assertEquals(2, s.streak)
    }

    @Test fun streakSurvivesUntilTodayEnds() {
        val s = stats(listOf(on(today.minusDays(1)), on(today.minusDays(2))), today, zone)
        assertEquals(2, s.streak)
        assertEquals(0, s.todayPoints)
    }

    @Test fun bestStreakLooksBackInHistory() {
        val days = (10L..14L).map { on(today.minusDays(it)) } + on(today)
        val s = stats(days, today, zone)
        assertEquals(1, s.streak)
        assertEquals(5, s.bestStreak)
    }

    @Test fun weekRunsMondayToSunday() {
        val s = stats(listOf(on(today, reps = 12), on(today.minusDays(5), reps = 4), on(today.minusDays(9))), today, zone)
        assertEquals(LocalDate.of(2026, 11, 2), s.week.first().day)
        assertEquals(listOf(4, 0, 0, 0, 0, 12, 0), s.week.map { it.points })
        assertEquals(12, s.todayReps)
        assertEquals(3, s.totalWorkouts)
    }

    @Test fun monthFormTrendAndMix() {
        val s = stats(
            listOf(
                on(today, reps = 20, form = 90, exercise = Exercise.SQUAT),
                on(today, reps = 10, form = 70),
                on(today.minusDays(40), reps = 99), // outside the 30-day window
                on(today.minusDays(2), reps = 6, tracked = false),
            ),
            today,
            zone,
        )
        assertEquals(30, s.month.size)
        assertEquals(today, s.month.last().day)
        assertEquals(80, s.formTrend.last()) // average of 90 and 70; the manual workout doesn't count
        assertEquals(null, s.formTrend[11])
        assertEquals(Exercise.SQUAT, s.mix.first().first)
    }

    @Test fun nutritionTargetsFollowMifflinStJeor() {
        // 70 kg, 175 cm, 25 y male: BMR = 700 + 1093.75 - 125 + 5 = 1673.75; × 1.55 = 2594
        val t = targets(Body(Sex.MALE, 25, 175, 70, Activity.MODERATE, Goal.MAINTAIN))
        assertEquals(2594, t.kcal)
        assertEquals(112, t.protein)
        assertTrue(abs(t.protein * 4 + t.carbs * 4 + t.fat * 9 - t.kcal) < 10)

        val cut = targets(Body(Sex.FEMALE, 30, 160, 55, Activity.LOW, Goal.LOSE))
        assertEquals(1200, cut.kcal) // never below the safe floor
    }
}
