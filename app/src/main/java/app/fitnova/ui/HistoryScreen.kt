package app.fitnova.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.Tab
import app.fitnova.domain.Mode
import app.fitnova.domain.PersonalBest
import app.fitnova.domain.Workout
import app.fitnova.domain.dayTotals
import app.fitnova.domain.focusDay
import app.fitnova.domain.heat
import app.fitnova.domain.monthGrid
import app.fitnova.domain.monthTotals
import app.fitnova.domain.personalBests
import app.fitnova.domain.workoutsByDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Points-colour strength per heat step (0 = no workouts). Even the top step keeps onSurface day numbers above 6:1. */
private val HeatAlpha = floatArrayOf(0f, 0.16f, 0.30f, 0.45f, 0.60f)

private class HistoryData(val byDay: Map<LocalDate, List<Workout>>, val totals: Map<LocalDate, Int>, val bests: List<PersonalBest>)

/** Full-screen history: month heatmap with totals, the chosen day's workouts (with delete), and personal bests. */
@Composable
fun HistoryScreen(vm: AppViewModel) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    // Grouping a long history is real work on a cheap phone: off the main thread, once per change.
    val data by produceState<HistoryData?>(null, workouts) {
        value = withContext(Dispatchers.Default) {
            val byDay = workoutsByDay(workouts)
            HistoryData(byDay, dayTotals(byDay), personalBests(workouts))
        }
    }
    val opened = vm.historyDay ?: today
    var selectedDay by rememberSaveable { mutableLongStateOf(opened.toEpochDay()) }
    var shownMonth by rememberSaveable { mutableLongStateOf(opened.withDayOfMonth(1).toEpochDay()) }
    var deleting by remember { mutableStateOf<Workout?>(null) }
    val gutter = shellGutter()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val locale = LocalConfiguration.current.locales[0]
    val time = timeFormat(locale)

    Column(Modifier.fillMaxSize()) {
        TopBar("History", onBack = vm::closeScreen, modifier = Modifier.statusBarsPadding().padding(horizontal = 4.dp))
        val d = data
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 4.dp, bottom = bottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                !vm.loaded || d == null -> Unit // a frame or two, while the page fades in
                d.byDay.isEmpty() -> item(key = "empty", contentType = "empty") {
                    Panel {
                        EmptyState("No history yet", illu = Illu.HISTORY_EMPTY, action = "Start", onAction = {
                            vm.closeScreen()
                            vm.tab = Tab.TRAIN
                        })
                    }
                }
                else -> {
                    val month = YearMonth.from(LocalDate.ofEpochDay(shownMonth))
                    val selected = LocalDate.ofEpochDay(selectedDay)
                    val first = YearMonth.from(d.byDay.keys.min())
                    item(key = "month", contentType = "month") {
                        MonthCard(
                            month, selected, today, d, vm.dailyGoal,
                            canBack = month > first,
                            canForward = month < YearMonth.from(today), // no future months
                            onMonth = { m ->
                                shownMonth = m.atDay(1).toEpochDay()
                                selectedDay = focusDay(m, d.byDay.keys, today).toEpochDay()
                            },
                            onDay = { selectedDay = it.toEpochDay() },
                        )
                    }
                    val day = d.byDay[selected].orEmpty()
                    item(key = "day", contentType = "title") {
                        SectionTitle(dayTitle(selected, today, locale), action = d.totals[selected]?.let { "$it pts" })
                    }
                    if (day.isEmpty()) {
                        item(key = "rest", contentType = "rest") { RestDay(selected == today) }
                    } else {
                        items(day, key = { it.id }, contentType = { "workout" }) { w -> DayWorkout(w, time) { deleting = w } }
                    }
                    if (d.bests.isNotEmpty()) {
                        item(key = "bests", contentType = "title") { SectionTitle("Personal bests", R.drawable.emoji_trophy) }
                        items(d.bests, key = { "best-${it.exercise.id}" }, contentType = { "best" }) { BestRow(it) }
                    }
                }
            }
        }
    }

    deleting?.let { w ->
        ConfirmDialog(
            "Delete workout?",
            "${w.exercise.title} · ${amountOf(w)} · +${w.points} pts",
            confirm = "Delete",
            destructive = true,
            onConfirm = { vm.deleteWorkout(w) },
            onDismiss = { deleting = null },
        )
    }
}

// ---- Month ----

@Composable
private fun MonthCard(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    data: HistoryData,
    goal: Int,
    canBack: Boolean,
    canForward: Boolean,
    onMonth: (YearMonth) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val title = remember(month, locale) { month.format(localizedPattern(locale, "MMMMyyyy")) }
    val spoken = remember(locale) { localizedPattern(locale, "EEEEdMMMM") }
    val weekdays = remember(locale) { DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) } }
    val weeks = remember(month) { monthGrid(month).chunked(7) }
    val sums = remember(data, month) { monthTotals(data.byDay, month) }
    Panel(padding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(R.drawable.ic_chevron_left, "Previous month", { onMonth(month.minusMonths(1)) }, container = Color.Transparent, enabled = canBack)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconCircleButton(R.drawable.ic_chevron_right, "Next month", { onMonth(month.plusMonths(1)) }, container = Color.Transparent, enabled = canForward)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 8.dp)) {
            Total(R.drawable.emoji_bolt, sums.points, "pts", "points")
            Total(R.drawable.emoji_lifter, sums.workouts, "workouts", "workouts")
            Total(R.drawable.emoji_calendar, sums.activeDays, "days", "active days")
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.clearAndSetSemantics {}) {
            weekdays.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        weeks.forEach { week ->
            Row {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val points = data.totals[day]
                        DayCell(
                            day,
                            heat = heat(points ?: 0, goal),
                            selected = day == selected,
                            isToday = day == today,
                            future = day.isAfter(today),
                            description = "${day.format(spoken)}, ${if (points == null) "no workouts" else "$points points"}",
                        ) { onDay(day) }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        HeatKey(goal)
    }
}

@Composable
private fun RowScope.Total(@DrawableRes emoji: Int, value: Int, label: String, spoken: String) {
    Column(
        Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$value $spoken" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Emoji(emoji, 16.dp)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A day tile tinted by its heat step; the selected day is filled (inverse), today wears a primary ring. */
@Composable
private fun RowScope.DayCell(
    day: LocalDate,
    heat: Int,
    selected: Boolean,
    isToday: Boolean,
    future: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val fill = when {
        selected -> colors.inverseSurface
        future -> Color.Transparent
        heat == 0 -> Nova.colors.track
        else -> Nova.colors.points.copy(alpha = HeatAlpha[heat])
    }
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .padding(2.dp)
            .clip(shape)
            .background(fill)
            .then(if (isToday) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .selectable(selected = selected, enabled = !future, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }, // after selectable, so its state and action stay
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${day.dayOfMonth}",
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> colors.inverseOnSurface
                future -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                else -> colors.onSurface
            },
        )
    }
}

/** The heat steps from none to goal hit; decorative, since every day reads out its points. */
@Composable
private fun HeatKey(goal: Int) {
    val c = Nova.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        HeatAlpha.forEachIndexed { i, a ->
            Box(Modifier.padding(start = 4.dp).size(12.dp).background(if (i == 0) c.track else c.points.copy(alpha = a), RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.width(8.dp))
        Text("Goal $goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Day and bests ----

@Composable
private fun DayWorkout(w: Workout, time: DateTimeFormatter, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Panel(padding = PaddingValues(0.dp)) {
        ListRow(
            w.exercise.title,
            subtitle = "${amountOf(w)} · ${if (w.tracked) "${w.form}%" else "Manual"} · ${Instant.ofEpochMilli(w.startedAt).atZone(ZoneId.systemDefault()).format(time)}",
            leading = { ExerciseTile(w.exercise, 40) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+${w.points}", style = MaterialTheme.typography.labelLarge)
                    IconCircleButton(R.drawable.ic_trash, "Delete ${w.exercise.title}", onDelete, container = Color.Transparent, tint = colors.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun RestDay(isToday: Boolean) {
    Panel(padding = PaddingValues(0.dp)) {
        ListRow(
            if (isToday) "Nothing yet today" else "Rest day",
            leading = { EmojiTile(if (isToday) R.drawable.emoji_sparkles else R.drawable.emoji_zzz, size = 40.dp) },
        )
    }
}

/** Most reps (or longest hold) for a move, with its best camera-checked form. */
@Composable
private fun BestRow(b: PersonalBest) {
    val hold = b.exercise.mode == Mode.HOLD
    val (value, unit) = when {
        !hold -> "${b.best}" to "reps"
        b.best < 60 -> "${b.best}" to "sec"
        else -> "${b.best / 60}:${(b.best % 60).toString().padStart(2, '0')}" to "min"
    }
    Panel(padding = PaddingValues(0.dp)) {
        ListRow(
            b.exercise.title,
            subtitle = listOfNotNull(b.form?.let { "Form $it%" }, if (b.workouts == 1) "1 workout" else "${b.workouts} workouts").joinToString(" · "),
            leading = { ExerciseTile(b.exercise, 40) },
            trailing = {
                Row {
                    Text(value, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.alignByBaseline())
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline().padding(start = 4.dp),
                    )
                }
            },
        )
    }
}

// ---- Helpers ----

private fun amountOf(w: Workout) = if (w.exercise.mode == Mode.HOLD) "${w.seconds} sec" else "${w.reps} reps"

private fun dayTitle(day: LocalDate, today: LocalDate, locale: Locale) = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(localizedPattern(locale, "EEEdMMM"))
}

/** Clock time in the phone's 12/24-hour setting. */
@Composable
private fun timeFormat(locale: Locale): DateTimeFormatter {
    val h24 = DateFormat.is24HourFormat(LocalContext.current)
    return remember(h24, locale) { DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", locale) }
}
