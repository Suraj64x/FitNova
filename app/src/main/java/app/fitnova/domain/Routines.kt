package app.fitnova.domain

import kotlin.math.roundToInt

/** One move of a routine: [target] reps, or seconds for a hold. */
data class Step(val exercise: Exercise, val target: Int) {
    /** Rough length including getting into position, for estimates only. */
    val seconds: Int
        get() = READY_SECONDS + if (exercise.mode == Mode.HOLD) target else (target * exercise.secondsPerRep()).roundToInt()
}

/**
 * A built-in circuit: [steps] in order with [restSeconds] between them. Each finished step is saved as its own
 * [Workout], so points, sync and badges work as usual. [id]s are stable: they may be stored.
 */
data class Routine(val id: String, val title: String, val emoji: String, val steps: List<Step>, val restSeconds: Int) {
    /** Work, getting ready and the rests between steps. */
    val seconds: Int get() = steps.sumOf { it.seconds } + restSeconds * (steps.size - 1).coerceAtLeast(0)

    /** Estimate for the card, rounded up. */
    val minutes: Int get() = (seconds + 59) / 60

    companion object {
        fun of(id: String) = ROUTINES.firstOrNull { it.id == id }
    }
}

/** Placing the phone, the get-ready check and the 3-2-1. */
private const val READY_SECONDS = 8

private fun Exercise.secondsPerRep(): Double = when (this) {
    Exercise.HIGH_KNEES, Exercise.MOUNTAIN_CLIMBER, Exercise.JUMP_ROPE -> 0.6 // every knee or jump counts
    Exercise.JACK -> 1.0
    Exercise.BURPEE -> 4.0
    else -> 2.5
}

private fun routine(id: String, title: String, emoji: String, rest: Int, vararg steps: Pair<Exercise, Int>) =
    Routine(id, title, emoji, steps.map { (e, t) -> Step(e, t) }, rest)

val ROUTINES: List<Routine> = listOf(
    routine(
        "wake_up", "Wake-up", "🌅", 20,
        Exercise.JACK to 30, Exercise.SQUAT to 15, Exercise.PLANK to 30, Exercise.HIGH_KNEES to 30,
    ),
    routine(
        "full_body", "Full body", "🏋️", 25,
        Exercise.SQUAT to 15, Exercise.KNEE_PUSHUP to 10, Exercise.GLUTE_BRIDGE to 15,
        Exercise.PLANK to 30, Exercise.JACK to 30, Exercise.LUNGE to 10,
    ),
    routine(
        "core_crusher", "Core crusher", "🔥", 25,
        Exercise.SITUP to 15, Exercise.LEG_RAISE to 12, Exercise.PLANK to 45,
        Exercise.MOUNTAIN_CLIMBER to 30, Exercise.SIDE_PLANK to 30,
    ),
    routine(
        "leg_day", "Leg day", "🦵", 30,
        Exercise.SQUAT to 20, Exercise.LUNGE to 12, Exercise.SUMO_SQUAT to 15,
        Exercise.WALL_SIT to 45, Exercise.GLUTE_BRIDGE to 15, Exercise.CALF_RAISE to 20,
    ),
    routine(
        "upper_push", "Upper push", "💪", 30,
        Exercise.PUSHUP to 12, Exercise.WIDE_PUSHUP to 10, Exercise.DIAMOND_PUSHUP to 8,
        Exercise.DIP to 12, Exercise.PRESS to 12,
    ),
    routine(
        "hiit_cardio", "HIIT cardio", "⚡", 20,
        Exercise.JACK to 40, Exercise.HIGH_KNEES to 30, Exercise.JUMP_SQUAT to 12,
        Exercise.MOUNTAIN_CLIMBER to 30, Exercise.BURPEE to 8,
    ),
    routine(
        "quiet", "Quiet (no jumps)", "🌙", 25,
        Exercise.SQUAT to 15, Exercise.PUSHUP to 10, Exercise.GLUTE_BRIDGE to 15,
        Exercise.PLANK to 40, Exercise.REVERSE_LUNGE to 10, Exercise.LEG_RAISE to 12,
    ),
    routine(
        "desk_break", "Desk break", "⏱️", 20,
        Exercise.SQUAT to 10, Exercise.DIP to 10, Exercise.LATERAL_RAISE to 12, Exercise.WALL_SIT to 30,
    ),
)
