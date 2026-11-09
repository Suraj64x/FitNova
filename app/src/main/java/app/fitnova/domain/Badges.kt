package app.fitnova.domain

import java.time.Instant
import java.time.ZoneId

data class Badge(val id: String, val emoji: String, val title: String, val earned: Boolean)

/**
 * Achievements, worked out on the phone from the (synced) workout history, so they match on every device.
 * Pure function: no storage, nothing to migrate.
 */
fun badges(all: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): List<Badge> {
    val s = stats(all, zone = zone)
    val totalReps = all.sumOf { it.reps }
    fun hour(w: Workout) = Instant.ofEpochMilli(w.startedAt).atZone(zone).hour
    return listOf(
        Badge("first", "🎉", "First workout", all.isNotEmpty()),
        Badge("streak3", "🔥", "3-day streak", s.bestStreak >= 3),
        Badge("streak7", "⚡", "7-day streak", s.bestStreak >= 7),
        Badge("streak30", "🏆", "30-day streak", s.bestStreak >= 30),
        Badge("reps100", "💯", "100 reps", totalReps >= 100),
        Badge("reps1000", "🚀", "1,000 reps", totalReps >= 1000),
        Badge("perfect", "🎯", "Perfect form", all.any { it.tracked && it.form == 100 && it.reps >= 10 }),
        Badge("plank2", "🧱", "2-min hold", all.any { it.reps == 0 && it.seconds >= 120 && it.tracked }),
        Badge("variety", "🌈", "10 moves", all.map { it.exercise }.distinct().size >= 10),
        Badge("early", "🌅", "Early bird", all.any { hour(it) < 7 }),
        Badge("owl", "🌙", "Night owl", all.any { hour(it) >= 22 }),
        Badge("points500", "⭐", "500 points", s.totalPoints >= 500),
    )
}

data class Level(val number: Int, val title: String, val into: Int, val span: Int) {
    val progress get() = if (span == 0) 1f else into.toFloat() / span
}

/** Level n starts at 50·n·(n−1) points: 0, 100, 300, 600, 1000, 1500… */
fun level(points: Int): Level {
    var n = 1
    while (50 * (n + 1) * n <= points) n++
    val start = 50 * n * (n - 1)
    val next = 50 * (n + 1) * n
    val title = when (n) {
        1 -> "Rookie"
        2 -> "Mover"
        3 -> "Regular"
        4 -> "Athlete"
        5 -> "Pro"
        else -> "Legend"
    }
    return Level(n, title, points - start, next - start)
}
