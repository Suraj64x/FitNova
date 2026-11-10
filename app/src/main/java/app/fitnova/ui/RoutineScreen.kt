package app.fitnova.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.WorkoutFlow
import app.fitnova.domain.Exercise
import app.fitnova.domain.Routine

/** Fluent artwork for a routine. */
internal val Routine.art: Int
    @DrawableRes get() = when (id) {
        "wake_up" -> R.drawable.emoji_sunrise
        "full_body" -> R.drawable.emoji_lifter
        "core_crusher" -> R.drawable.emoji_fire
        "leg_day" -> R.drawable.emoji_leg
        "upper_push" -> R.drawable.emoji_biceps
        "hiit_cardio" -> R.drawable.emoji_bolt
        "quiet" -> R.drawable.emoji_moon
        else -> R.drawable.emoji_stopwatch
    }

/** Routine tint from the colour language: coral energy, indigo, the category colours, violet for the calm one. */
internal fun NovaColors.of(routine: Routine): Color = when (routine.id) {
    "wake_up" -> minutes
    "full_body" -> points
    "core_crusher" -> core
    "leg_day" -> lower
    "upper_push" -> upper
    "hiit_cardio" -> cardio
    "quiet" -> sleep
    else -> water
}

/** Carousel card: artwork, title, minutes and moves, on the routine's tint. */
@Composable
internal fun RoutineCard(routine: Routine, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .width(156.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Nova.colors.of(routine).copy(alpha = 0.14f).compositeOver(colors.surfaceContainer))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Emoji(routine.art, 40.dp)
        Spacer(Modifier.height(12.dp))
        Text(routine.title, style = MaterialTheme.typography.titleMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(R.drawable.ic_timer, size = 16, tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                "${routine.minutes} min · ${routine.steps.size} moves",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Preview before starting: the steps with their targets, camera and voice switches, a pinned Start. */
@Composable
internal fun RoutineSheet(routine: Routine, vm: AppViewModel, onStart: (camera: Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var camera by rememberSaveable(routine.id) { mutableStateOf(true) }
    val tracked = routine.steps.any { it.exercise.trackable }
    WarmCamera(camera && tracked)
    NovaSheet(onDismiss, footer = { PrimaryButton("Start", onClick = { onStart(camera && tracked) }) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(routine.art, Nova.colors.of(routine), 64.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(routine.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${routine.minutes} min · ${routine.steps.size} moves · ${routine.restSeconds} s rest",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Panel(padding = PaddingValues(vertical = 4.dp)) {
            routine.steps.forEach { step ->
                ListRow(
                    step.exercise.title,
                    leading = { ExerciseTile(step.exercise, 44) },
                    trailing = {
                        Text(step.exercise.targetLabel(step.target), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    },
                )
            }
        }
        Panel(padding = PaddingValues(vertical = 4.dp)) {
            if (tracked) {
                ToggleRow(
                    R.drawable.ic_camera_duo, "Camera",
                    subtitle = if (camera) "Counts reps · full points" else "Off · half points",
                    checked = camera,
                    onCheckedChange = { camera = it },
                )
            }
            ToggleRow(R.drawable.ic_speaker_duo, "Voice coach", checked = vm.voice, onCheckedChange = vm::changeVoice)
        }
    }
}

/**
 * Rest before a routine step: a countdown dial, +10 s and Skip, and the move coming [next] with its camera
 * placement. [left] and [total] are seconds; only the dial reads them, so a tick redraws just that.
 */
@Composable
internal fun RestScreen(
    next: Exercise,
    target: Int,
    camera: Boolean,
    left: () -> Int,
    total: () -> Int,
    onMore: () -> Unit,
    onSkip: () -> Unit,
    top: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        top()
        Spacer(Modifier.weight(1f))
        Text("Rest", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        RestDial(left, total)
        Spacer(Modifier.weight(1f))
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseTile(next, 56)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Next", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Text(next.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${next.targetLabel(target)} · ${if (camera) next.cameraTip else "By hand"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("+10 s", onMore, Modifier.weight(1f))
            PrimaryButton("Skip", onSkip, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RestDial(left: () -> Int, total: () -> Int) {
    val seconds = left()
    Arc(
        progress = seconds / total().coerceAtLeast(1).toFloat(),
        color = Nova.colors.points,
        size = 220.dp,
        stroke = 14.dp,
        contentDescription = "$seconds seconds of rest left",
    ) {
        Text("$seconds", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
    }
}

/** A routine's summary: total points, moves done, time and form, then each saved step. */
@Composable
internal fun RoutineSummary(done: WorkoutFlow.Done, onDone: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val routine = done.routine ?: return
    val steps = done.steps
    val tracked = remember(steps) { steps.filter { it.tracked } }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(colors.background).systemBarsPadding()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "head", contentType = "head") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Illustration(Illu.CELEBRATE, height = 120.dp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Emoji(routine.art, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(routine.title, style = MaterialTheme.typography.titleLarge)
                    }
                    PointsHeadline(steps.sumOf { it.points })
                    done.badge?.let { Tag("${it.emoji} ${it.title}", tone = colors.primary) }
                }
            }
            item(key = "stats", contentType = "stats") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Moves", "${steps.size}/${routine.steps.size}", Modifier.weight(1f))
                    StatTile("Time", clock(steps.sumOf { it.seconds }), Modifier.weight(1f))
                    StatTile("Form", if (tracked.isEmpty()) "–" else "${tracked.sumOf { it.form } / tracked.size}%", Modifier.weight(1f))
                }
            }
            items(steps, key = { it.id }, contentType = { "step" }) { w ->
                Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    ListRow(
                        w.exercise.title,
                        w.summaryLine(),
                        leading = { ExerciseTile(w.exercise, 40) },
                        trailing = { Text("+${w.points}", style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Done", onDone)
            SecondaryButton("Delete", { confirm = true }, color = Nova.colors.bad)
        }
    }
    if (confirm) {
        ConfirmDialog(
            if (steps.size == 1) "Delete workout?" else "Delete ${steps.size} workouts?",
            confirm = "Delete",
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { confirm = false },
        )
    }
}
