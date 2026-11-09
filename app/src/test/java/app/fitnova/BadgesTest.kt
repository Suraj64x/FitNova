package app.fitnova

import app.fitnova.domain.Exercise
import app.fitnova.domain.Workout
import app.fitnova.domain.badges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BadgesTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.now(zone)

    private fun w(
        daysAgo: Long = 0,
        hour: Int = 9,
        exercise: Exercise = Exercise.PUSHUP,
        reps: Int = 10,
        seconds: Int = 60,
        form: Int = 80,
        tracked: Boolean = true,
    ) = Workout(
        id = "$daysAgo-$hour-${exercise.id}-$reps",
        exercise = exercise,
        reps = reps,
        seconds = seconds,
        form = form,
        tracked = tracked,
        startedAt = day.minusDays(daysAgo).atTime(hour, 0).toInstant(zone).toEpochMilli(),
    )

    private fun earned(list: List<Workout>) = badges(list, zone).filter { it.earned }.map { it.id }.toSet()

    @Test fun nothingEarnedWithoutWorkouts() {
        assertTrue(earned(emptyList()).isEmpty())
        assertEquals(12, badges(emptyList(), zone).size)
    }

    @Test fun firstWorkoutAndStreaks() {
        val e = earned(listOf(w(0), w(1), w(2)))
        assertTrue("first" in e)
        assertTrue("streak3" in e)
        assertFalse("streak7" in e)
    }

    @Test fun repsPerfectFormAndHolds() {
        val e = earned(
            listOf(
                w(reps = 60, form = 100),
                w(reps = 50, hour = 10),
                w(exercise = Exercise.PLANK, reps = 0, seconds = 125, hour = 11),
            ),
        )
        assertTrue("reps100" in e)
        assertTrue("perfect" in e)
        assertTrue("plank2" in e)
        assertFalse("reps1000" in e)
    }

    @Test fun manualWorkoutsDontEarnFormBadges() {
        val e = earned(listOf(w(reps = 20, form = 100, tracked = false), w(exercise = Exercise.PLANK, reps = 0, seconds = 300, tracked = false)))
        assertFalse("perfect" in e)
        assertFalse("plank2" in e)
    }

    @Test fun timeOfDayAndVariety() {
        val ten = Exercise.entries.take(10).mapIndexed { i, ex -> w(hour = 12, exercise = ex, reps = 5 + i) }
        val e = earned(ten + w(hour = 6, reps = 3) + w(hour = 23, reps = 4))
        assertTrue("variety" in e)
        assertTrue("early" in e)
        assertTrue("owl" in e)
    }
}
