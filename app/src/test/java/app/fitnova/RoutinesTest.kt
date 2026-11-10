package app.fitnova

import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode
import app.fitnova.domain.ROUTINES
import app.fitnova.domain.Routine
import app.fitnova.domain.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinesTest {
    @Test
    fun builtInsKeepTheirStableIds() {
        assertEquals(
            listOf("wake_up", "full_body", "core_crusher", "leg_day", "upper_push", "hiit_cardio", "quiet", "desk_break"),
            ROUTINES.map { it.id },
        )
        assertSame(ROUTINES[3], Routine.of("leg_day"))
        assertNull(Routine.of("nope"))
    }

    @Test
    fun everyRoutineIsUsable() {
        for (r in ROUTINES) {
            assertTrue(r.id, r.restSeconds in 20..30)
            assertTrue(r.id, r.steps.size in 4..6)
            assertTrue(r.id, r.steps.all { it.target > 0 })
            assertTrue(r.id, r.minutes in 3..12)
        }
    }

    @Test
    fun stepsFollowTheSpec() {
        val wake = Routine.of("wake_up")!!.steps
        assertEquals(listOf(Exercise.JACK, Exercise.SQUAT, Exercise.PLANK, Exercise.HIGH_KNEES), wake.map { it.exercise })
        assertEquals(listOf(30, 15, 30, 30), wake.map { it.target })
        // Holds are timed in seconds.
        assertEquals(Mode.HOLD, Routine.of("leg_day")!!.steps[3].exercise.mode)
        assertEquals(Step(Exercise.BURPEE, 8), Routine.of("hiit_cardio")!!.steps.last())
        // No jumping in the quiet one.
        val jumps = setOf(Exercise.JACK, Exercise.JUMP_SQUAT, Exercise.BURPEE, Exercise.JUMP_ROPE, Exercise.HIGH_KNEES)
        assertTrue(Routine.of("quiet")!!.steps.none { it.exercise in jumps })
    }

    @Test
    fun estimateAddsWorkReadyAndRest() {
        // Hold: 8 s ready + 60 s; reps: 8 s ready + 10 × 2.5 s; one 20 s rest between them.
        val r = Routine("t", "T", "", listOf(Step(Exercise.PLANK, 60), Step(Exercise.SQUAT, 10)), 20)
        assertEquals(68 + 33 + 20, r.seconds)
        assertEquals(3, r.minutes) // 121 s rounds up
        assertEquals(4, Routine.of("wake_up")!!.minutes)
    }
}
