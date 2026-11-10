package app.fitnova.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The cells of a Monday-first month calendar: a null for each blank before the 1st, then every day of [month]. */
fun monthGrid(month: YearMonth): List<LocalDate?> =
    List(month.atDay(1).dayOfWeek.value - 1) { null } + List(month.lengthOfMonth()) { month.atDay(it + 1) }

/** Workouts by the local day they started on. Each day keeps the order of [all] (newest first from the store). */
fun workoutsByDay(all: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Workout>> =
    all.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }

/** Points per day. */
fun dayTotals(byDay: Map<LocalDate, List<Workout>>): Map<LocalDate, Int> = byDay.mapValues { (_, list) -> list.sumOf { it.points } }

data class MonthTotals(val points: Int, val workouts: Int, val activeDays: Int)

fun monthTotals(byDay: Map<LocalDate, List<Workout>>, month: YearMonth): MonthTotals {
    val days = byDay.filterKeys { YearMonth.from(it) == month }.values
    return MonthTotals(days.sumOf { list -> list.sumOf { it.points } }, days.sumOf { it.size }, days.size)
}

/** Heatmap step for a day: 0 = no points, then under a quarter of the daily [goal], under half, under the goal, goal hit. */
fun heat(points: Int, goal: Int): Int {
    val g = goal.coerceAtLeast(1)
    return when {
        points <= 0 -> 0
        points * 4 < g -> 1
        points * 2 < g -> 2
        points < g -> 3
        else -> 4
    }
}

/** The day to show when a month opens: its latest active day, else its last day (never after [today]). */
fun focusDay(month: YearMonth, active: Collection<LocalDate>, today: LocalDate): LocalDate =
    active.filter { YearMonth.from(it) == month && !it.isAfter(today) }.maxOrNull() ?: minOf(month.atEndOfMonth(), today)

/**
 * Best single workout of an exercise: [best] is the most reps, or the longest hold in seconds for holds.
 * [form] is the best camera-checked form (manual workouts have none); null when the move was never tracked.
 */
data class PersonalBest(val exercise: Exercise, val best: Int, val form: Int?, val workouts: Int)

/** One best per exercise, most practised first. */
fun personalBests(all: List<Workout>): List<PersonalBest> =
    all.groupBy { it.exercise }
        .map { (ex, list) ->
            PersonalBest(
                exercise = ex,
                best = list.maxOf { if (ex.mode == Mode.HOLD) it.seconds else it.reps },
                form = list.filter { it.tracked }.maxOfOrNull { it.form },
                workouts = list.size,
            )
        }
        .filter { it.best > 0 }
        .sortedWith(compareByDescending<PersonalBest> { it.workouts }.thenBy { it.exercise.ordinal })
