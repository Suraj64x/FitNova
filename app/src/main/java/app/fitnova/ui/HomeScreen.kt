package app.fitnova.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.Tab
import app.fitnova.data.SyncState
import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode
import app.fitnova.domain.Stats
import app.fitnova.domain.Workout
import app.fitnova.domain.intake
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DefaultQuick = listOf(Exercise.PUSHUP, Exercise.SQUAT, Exercise.PLANK, Exercise.JACK, Exercise.LUNGE, Exercise.CURL, Exercise.HIGH_KNEES)

@Composable
fun HomeScreen(vm: AppViewModel, padding: PaddingValues) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val pending = workouts.count { !it.synced }
    var range by rememberSaveable { mutableIntStateOf(0) }
    val quick = (workouts.map { it.exercise } + DefaultQuick).distinct().take(8)

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 18.dp, bottom = padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(greeting(vm.username), style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                }
                SyncBadge(sync, pending, signedIn = vm.username != null, onClick = vm::syncNow)
            }
        }
        item { TodayCard(stats, vm.dailyGoal, meals.intake().kcal, vm.targets?.kcal) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(R.drawable.ic_flame, tint = Tone.warn, size = 20)
                        Spacer(Modifier.width(6.dp))
                        Text("Streak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    BigNumber("${stats.streak}", if (stats.streak == 1) "day" else "days")
                    Text("Best ${stats.bestStreak}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel(Modifier.weight(1f)) {
                    Text("All time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    BigNumber("${stats.totalPoints}", "pts")
                    Text("${stats.totalWorkouts} workouts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { ActivityCard(stats, today, range) { range = it } }
        item { FormCard(stats.formTrend) }
        if (stats.mix.isNotEmpty()) item { MixCard(stats.mix) }
        item { SectionTitle("Quick start", action = "See all", onAction = { vm.tab = Tab.TRAIN }) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quick, key = { it.id }) { ex ->
                    Panel(Modifier.width(128.dp), onClick = { vm.setup = ex }, padding = PaddingValues(10.dp)) {
                        ExerciseTile(ex, 106)
                        Spacer(Modifier.height(8.dp))
                        Text(ex.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${ex.defaultTarget} ${ex.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SectionTitle("Recent") }
        item {
            if (workouts.isEmpty()) {
                Panel {
                    EmptyNote("No workouts yet", "Pick an exercise and the camera will count reps and check your form.")
                    PrimaryButton("Start a workout", onClick = { vm.tab = Tab.TRAIN })
                }
            } else {
                Panel(padding = PaddingValues(0.dp)) {
                    val recent = workouts.take(6)
                    recent.forEachIndexed { i, w ->
                        WorkoutRow(w)
                        if (i < recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigNumber(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.width(4.dp))
        Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
private fun TodayCard(stats: Stats, goal: Int, eaten: Int, kcalTarget: Int?) {
    val done = stats.todayPoints >= goal
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${stats.todayPoints}", style = MaterialTheme.typography.displayMedium)
                    Text(
                        " / $goal pts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Text(
                    if (done) "Daily goal reached" else "${goal - stats.todayPoints} pts to your daily goal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) Tone.good else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Ring(stats.todayPoints.toFloat() / goal, Modifier.size(104.dp), color = if (done) Tone.good else Tone.accent, stroke = 12.dp) {
                Text("${(stats.todayPoints * 100 / goal).coerceAtMost(999)}%", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(14.dp))
        Row {
            MiniStat("Reps", "${stats.todayReps}", null)
            MiniStat("Active", "${stats.todayMinutes}", "min")
            MiniStat("Eaten", "$eaten", if (kcalTarget != null) "/ $kcalTarget" else "kcal")
        }
    }
}

@Composable
private fun RowScope.MiniStat(label: String, value: String, unit: String?) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun ActivityCard(stats: Stats, today: LocalDate, range: Int, onRange: (Int) -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val total = if (range == 0) stats.week.sumOf { it.points } else stats.month.sumOf { it.points }
            Text("$total pts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Segmented(listOf("This week", "30 days"), range, onRange)
        Spacer(Modifier.height(16.dp))
        if (range == 0) {
            WeekBars(stats.week, today)
        } else {
            LineChart(stats.month.map { it.points.toFloat() }, Modifier.fillMaxWidth().height(130.dp))
            Spacer(Modifier.height(8.dp))
            Row {
                Text(stats.month.first().day.format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            val active = stats.month.count { it.points > 0 }
            Text(
                "Active on $active of 30 days · ${stats.month.sumOf { it.points } / 30} pts a day on average",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FormCard(trend: List<Int?>) {
    val known = trend.filterNotNull()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Form score", style = MaterialTheme.typography.titleMedium)
                Text("Last 14 days, camera workouts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (known.isNotEmpty()) {
                Text("${known.average().toInt()}%", style = MaterialTheme.typography.headlineSmall, color = Tone.good)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (known.isEmpty()) {
            Text(
                "Turn on the camera form check to see how your technique improves.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LineChart(trend.map { it?.toFloat() }, Modifier.fillMaxWidth().height(100.dp), color = Tone.good, max = 100f)
        }
    }
}

@Composable
private fun MixCard(mix: List<Pair<Exercise, Int>>) {
    val max = mix.maxOf { it.second }.coerceAtLeast(1)
    Panel {
        Text("Exercise mix", style = MaterialTheme.typography.titleMedium)
        Text("Points by exercise, last 30 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        mix.forEach { (ex, pts) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ExerciseTile(ex, 40)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row {
                        Text(ex.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("$pts pts", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                    }
                    Spacer(Modifier.height(6.dp))
                    Meter(pts.toFloat() / max, Tone.accent, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun greeting(name: String?): String {
    val part = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name == null) part else "$part,\n$name"
}

@Composable
private fun SyncBadge(state: SyncState, pending: Int, signedIn: Boolean, onClick: () -> Unit) {
    val (text, tone) = when {
        !signedIn -> "On this phone" to MaterialTheme.colorScheme.onSurfaceVariant
        state is SyncState.Running -> "Syncing" to Tone.accent
        state is SyncState.Failed && state.offline -> (if (pending > 0) "Offline · $pending" else "Offline") to Tone.warn
        state is SyncState.Failed -> "Sync issue" to Tone.bad
        pending > 0 -> "$pending to sync" to Tone.warn
        else -> "Synced" to Tone.good
    }
    Box(Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = signedIn, onClick = onClick)) {
        Pill(text, tone)
    }
}

@Composable
fun WorkoutRow(w: Workout) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ExerciseTile(w.exercise, 44)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(w.exercise.title, style = MaterialTheme.typography.titleSmall)
            val amount = if (w.exercise.mode == Mode.HOLD) "${w.seconds}s held" else "${w.reps} reps"
            val detail = if (w.tracked) "${w.form}% form" else "manual"
            val now = System.currentTimeMillis()
            val ago = if (now - w.startedAt < DateUtils.MINUTE_IN_MILLIS) "just now" else DateUtils.getRelativeTimeSpanString(w.startedAt, now, DateUtils.MINUTE_IN_MILLIS)
            Text("$amount · $detail · $ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("+${w.points}", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
    }
}
