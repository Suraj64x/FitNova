package app.fitnova.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
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
private val Water = Color(0xFF38B2E8)
private const val MINUTES_GOAL = 30
private const val WATER_GOAL = 8

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
    val cardPx = with(LocalDensity.current) { 160.dp.roundToPx() }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 18.dp, bottom = padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(greeting(vm.username), style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                }
                SyncBadge(sync, pending, signedIn = vm.username != null, onClick = vm::syncNow)
            }
        }
        item { TodayCard(stats, vm.dailyGoal, vm.water, meals.intake().kcal, vm.targets?.kcal, onWater = { vm.addWater(1) }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f)) {
                    Text("🔥 Streak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    BigNumber("${stats.streak}", if (stats.streak == 1) "day" else "days")
                    Text("Best ${stats.bestStreak}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel(Modifier.weight(1f)) {
                    Text("🏆 All time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    BigNumber("${stats.totalPoints}", "pts")
                    Text("${stats.totalWorkouts} workouts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionTitle("Quick start", action = "See all", onAction = { vm.tab = Tab.TRAIN }) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quick, key = { it.id }) { ex ->
                    val photo = vm.exercisePhotos[ex.id]
                    if (photo != null) {
                        PhotoCard(
                            photo.path, ex.title, "${ex.defaultTarget} ${ex.unit}", cardPx, onClick = { vm.setup = ex },
                            modifier = Modifier.size(width = 150.dp, height = 180.dp),
                        )
                    } else {
                        Panel(Modifier.width(150.dp), onClick = { vm.setup = ex }, padding = PaddingValues(10.dp)) {
                            ExerciseTile(ex, 128)
                            Spacer(Modifier.height(8.dp))
                            Text(ex.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item { ActivityCard(stats, today, range) { range = it } }
        item { FormCard(stats.formTrend) }
        if (stats.mix.isNotEmpty()) item { MixCard(stats.mix) }
        item { SectionTitle("Recent") }
        item {
            if (workouts.isEmpty()) {
                Panel {
                    EmptyNote("No workouts yet", figure = Exercise.JACK)
                    PrimaryButton("Start", onClick = { vm.tab = Tab.TRAIN })
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

/** Three concentric rings for today: points, active minutes and water. */
@Composable
private fun TodayCard(stats: Stats, goal: Int, water: Int, eaten: Int, kcalTarget: Int?, onWater: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rings(
                listOf(
                    stats.todayPoints.toFloat() / goal to Tone.accent,
                    stats.todayMinutes.toFloat() / MINUTES_GOAL to Tone.good,
                    water.toFloat() / WATER_GOAL to Water,
                ),
                Modifier.size(132.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RingLegend("⚡", "${stats.todayPoints}", "/ $goal pts", Tone.accent)
                RingLegend("⏱", "${stats.todayMinutes}", "/ $MINUTES_GOAL min", Tone.good)
                RingLegend("💧", "$water", "/ $WATER_GOAL  +1", Water, Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onWater))
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row {
            MiniStat("🏋️", "${stats.todayReps}", "reps", Modifier.weight(1f))
            MiniStat("🍽️", "$eaten", if (kcalTarget != null) "/ $kcalTarget kcal" else "kcal", Modifier.weight(1.4f))
        }
    }
}

@Composable
private fun RingLegend(emoji: String, value: String, rest: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text("$emoji ", style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        Text(" $rest", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniStat(emoji: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.width(4.dp))
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivityCard(stats: Stats, today: LocalDate, range: Int, onRange: (Int) -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📈 Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val total = if (range == 0) stats.week.sumOf { it.points } else stats.month.sumOf { it.points }
            Text("$total pts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Segmented(listOf("Week", "30 days"), range, onRange)
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
        }
    }
}

@Composable
private fun FormCard(trend: List<Int?>) {
    val known = trend.filterNotNull()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎯 Form", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                if (known.isEmpty()) "–" else "${known.average().toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                color = if (known.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Tone.good,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (known.isEmpty()) {
            Text("📷 Use camera mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LineChart(trend.map { it?.toFloat() }, Modifier.fillMaxWidth().height(100.dp), color = Tone.good, max = 100f)
        }
    }
}

@Composable
private fun MixCard(mix: List<Pair<Exercise, Int>>) {
    val max = mix.maxOf { it.second }.coerceAtLeast(1)
    Panel {
        Text("📊 Mix", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
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
    return if (name == null) "$part 👋" else "$part,\n$name 👋"
}

@Composable
private fun SyncBadge(state: SyncState, pending: Int, signedIn: Boolean, onClick: () -> Unit) {
    val (text, tone) = when {
        !signedIn -> "Offline" to MaterialTheme.colorScheme.onSurfaceVariant
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
            val amount = if (w.exercise.mode == Mode.HOLD) "${w.seconds}s" else "${w.reps} reps"
            val detail = if (w.tracked) "${w.form}%" else "manual"
            val now = System.currentTimeMillis()
            val ago = if (now - w.startedAt < DateUtils.MINUTE_IN_MILLIS) "now" else DateUtils.getRelativeTimeSpanString(w.startedAt, now, DateUtils.MINUTE_IN_MILLIS)
            Text("$amount · $detail · $ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("+${w.points}", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
    }
}
