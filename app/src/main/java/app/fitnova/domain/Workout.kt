package app.fitnova.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class Mode { REPS, HOLD }

enum class Category(val label: String) { UPPER("Upper body"), LOWER("Lower body"), CORE("Core"), CARDIO("Cardio") }

/** Which posture rules the camera coach applies. [NONE] = counted by hand (no reliable camera signal). */
enum class Rule { PUSH, KNEE_PUSH, DIP, SQUAT, LUNGE, CURL, PRESS, RAISE, JACK, BRIDGE, SITUP, LEG_RAISE, KNEE_DRIVE, PLANK, WALL_SIT, NONE }

enum class Exercise(
    val id: String,
    val title: String,
    val category: Category,
    val rule: Rule,
    val defaultTarget: Int,
    val muscles: String,
    val cameraTip: String,
) {
    PUSHUP("pushup", "Push-ups", Category.UPPER, Rule.PUSH, 15, "Chest · Triceps · Core", SIDE_FLOOR),
    KNEE_PUSHUP("knee_pushup", "Knee push-ups", Category.UPPER, Rule.KNEE_PUSH, 15, "Chest · Triceps", SIDE_FLOOR),
    WIDE_PUSHUP("wide_pushup", "Wide push-ups", Category.UPPER, Rule.PUSH, 12, "Chest · Shoulders", SIDE_FLOOR),
    DIAMOND_PUSHUP("diamond_pushup", "Diamond push-ups", Category.UPPER, Rule.PUSH, 10, "Triceps · Chest", SIDE_FLOOR),
    DIP("dip", "Chair dips", Category.UPPER, Rule.DIP, 12, "Triceps · Shoulders", SIDE_BODY),
    CURL("curl", "Bicep curls", Category.UPPER, Rule.CURL, 12, "Biceps", SIDE_UPPER),
    HAMMER_CURL("hammer_curl", "Hammer curls", Category.UPPER, Rule.CURL, 12, "Biceps · Forearms", SIDE_UPPER),
    PRESS("press", "Shoulder press", Category.UPPER, Rule.PRESS, 12, "Shoulders · Triceps", FRONT_UPPER),
    LATERAL_RAISE("lateral_raise", "Lateral raises", Category.UPPER, Rule.RAISE, 12, "Side shoulders", FRONT_UPPER),

    SQUAT("squat", "Squats", Category.LOWER, Rule.SQUAT, 20, "Quads · Glutes", SIDE_BODY),
    SUMO_SQUAT("sumo_squat", "Sumo squats", Category.LOWER, Rule.SQUAT, 15, "Inner thighs · Glutes", SIDE_BODY),
    JUMP_SQUAT("jump_squat", "Jump squats", Category.LOWER, Rule.SQUAT, 12, "Legs · Power", SIDE_BODY),
    LUNGE("lunge", "Lunges", Category.LOWER, Rule.LUNGE, 12, "Quads · Glutes · Balance", SIDE_BODY),
    REVERSE_LUNGE("reverse_lunge", "Reverse lunges", Category.LOWER, Rule.LUNGE, 12, "Glutes · Hamstrings", SIDE_BODY),
    GLUTE_BRIDGE("glute_bridge", "Glute bridges", Category.LOWER, Rule.BRIDGE, 15, "Glutes · Hamstrings", SIDE_FLOOR),
    WALL_SIT("wall_sit", "Wall sit", Category.LOWER, Rule.WALL_SIT, 45, "Quads", SIDE_BODY),
    CALF_RAISE("calf_raise", "Calf raises", Category.LOWER, Rule.NONE, 20, "Calves", ""),

    PLANK("plank", "Plank", Category.CORE, Rule.PLANK, 45, "Core · Shoulders", SIDE_FLOOR),
    SIDE_PLANK("side_plank", "Side plank", Category.CORE, Rule.PLANK, 30, "Obliques · Core", "Face the camera, whole body in frame."),
    SITUP("situp", "Sit-ups", Category.CORE, Rule.SITUP, 15, "Abs · Hip flexors", SIDE_FLOOR),
    CRUNCH("crunch", "Crunches", Category.CORE, Rule.NONE, 20, "Upper abs", ""),
    LEG_RAISE("leg_raise", "Leg raises", Category.CORE, Rule.LEG_RAISE, 12, "Lower abs", SIDE_FLOOR),
    MOUNTAIN_CLIMBER("mountain_climber", "Mountain climbers", Category.CORE, Rule.KNEE_DRIVE, 30, "Core · Cardio", SIDE_FLOOR),

    JACK("jack", "Jumping jacks", Category.CARDIO, Rule.JACK, 30, "Full body · Cardio", FRONT_BODY),
    HIGH_KNEES("high_knees", "High knees", Category.CARDIO, Rule.KNEE_DRIVE, 30, "Cardio · Hip flexors", FRONT_BODY),
    BURPEE("burpee", "Burpees", Category.CARDIO, Rule.NONE, 10, "Full body", ""),
    JUMP_ROPE("jump_rope", "Jump rope", Category.CARDIO, Rule.NONE, 100, "Cardio · Calves", "");

    val mode get() = if (rule == Rule.PLANK || rule == Rule.WALL_SIT) Mode.HOLD else Mode.REPS
    val trackable get() = rule != Rule.NONE
    val unit get() = if (mode == Mode.HOLD) "sec" else "reps"

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

private const val SIDE_FLOOR = "Phone on the floor, side-on, whole body in frame."
private const val SIDE_BODY = "Side-on to the camera, whole body in frame."
private const val SIDE_UPPER = "Side-on to the camera, hips to head in frame."
private const val FRONT_UPPER = "Face the camera, hips to head in frame."
private const val FRONT_BODY = "Face the camera, whole body in frame."

/**
 * Leaderboard points: reps scored by form, or 1 point per 2 seconds for holds (holds store reps = 0).
 * Manual workouts count half. Mirrored by public.fn_points() in the Supabase migration.
 */
fun points(reps: Int, seconds: Int, form: Int, tracked: Boolean): Int {
    val volume = if (reps == 0) seconds / 2 else reps
    val multiplier = if (tracked) form / 100.0 else 0.5
    return (volume * multiplier).roundToInt()
}

data class Workout(
    val id: String,
    val exercise: Exercise,
    val reps: Int,
    val seconds: Int,
    val form: Int,
    val tracked: Boolean,
    val startedAt: Long,
    val synced: Boolean = false,
) {
    val points get() = points(reps, seconds, form, tracked)
}

data class DayTotal(val day: LocalDate, val points: Int)

data class Stats(
    val todayPoints: Int,
    val todayReps: Int,
    val todayMinutes: Int,
    val streak: Int,
    val bestStreak: Int,
    val week: List<DayTotal>,
    /** Last 30 days, oldest first. */
    val month: List<DayTotal>,
    /** Average form score of camera-tracked workouts for each of the last 14 days (null = none that day). */
    val formTrend: List<Int?>,
    /** Top exercises by points over the last 30 days. */
    val mix: List<Pair<Exercise, Int>>,
    val totalWorkouts: Int,
    val totalPoints: Int,
)

fun stats(all: List<Workout>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Stats {
    val byDay = all.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
    val todays = byDay[today].orEmpty()

    // A streak stays alive until today ends, so start counting from yesterday if today is still empty.
    var day = if (byDay.containsKey(today)) today else today.minusDays(1)
    var streak = 0
    while (byDay.containsKey(day)) {
        streak++
        day = day.minusDays(1)
    }

    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (d in byDay.keys.sorted()) {
        run = if (previous != null && previous.plusDays(1) == d) run + 1 else 1
        best = maxOf(best, run)
        previous = d
    }

    fun total(d: LocalDate) = DayTotal(d, byDay[d].orEmpty().sumOf { it.points })
    val monday = today.with(DayOfWeek.MONDAY)
    val monthStart = today.minusDays(29)

    val formTrend = (13 downTo 0).map { back ->
        val tracked = byDay[today.minusDays(back.toLong())].orEmpty().filter { it.tracked }
        if (tracked.isEmpty()) null else tracked.sumOf { it.form } / tracked.size
    }
    val mix = all.filter { !Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().isBefore(monthStart) }
        .groupBy { it.exercise }
        .map { (ex, list) -> ex to list.sumOf { it.points } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(5)

    return Stats(
        todayPoints = todays.sumOf { it.points },
        todayReps = todays.sumOf { it.reps },
        todayMinutes = (todays.sumOf { it.seconds } + 30) / 60,
        streak = streak,
        bestStreak = maxOf(best, streak),
        week = (0L..6L).map { total(monday.plusDays(it)) },
        month = (0L..29L).map { total(monthStart.plusDays(it)) },
        formTrend = formTrend,
        mix = mix,
        totalWorkouts = all.size,
        totalPoints = all.sumOf { it.points },
    )
}
