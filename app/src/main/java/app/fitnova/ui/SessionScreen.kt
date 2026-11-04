package app.fitnova.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import app.fitnova.R
import app.fitnova.WorkoutFlow
import app.fitnova.domain.Mode
import app.fitnova.domain.Workout
import app.fitnova.pose.Coach
import app.fitnova.pose.Feedback
import app.fitnova.pose.Frame
import app.fitnova.pose.PoseCamera
import app.fitnova.pose.SkeletonOverlay
import app.fitnova.pose.Status
import kotlinx.coroutines.delay
import java.util.UUID

private val Searching = Color(0xFF7D848D)

private fun Status.color() = when (this) {
    Status.GOOD -> Tone.good
    Status.FIX -> Tone.warn
    Status.SEARCHING -> Searching
}

fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun SessionScreen(live: WorkoutFlow.Live, onDone: (Workout?) -> Unit) {
    val exercise = live.exercise
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    var cameraOn by remember {
        mutableStateOf(live.camera && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asking by remember { mutableStateOf(live.camera && !cameraOn) }
    // The clock starts once the workout really starts, not while the permission prompt is open.
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraOn = it
        asking = false
        startedAt = System.currentTimeMillis()
    }
    LaunchedEffect(Unit) { if (asking) permission.launch(Manifest.permission.CAMERA) }

    val coach = remember { Coach(exercise) }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var feedback by remember { mutableStateOf(coach.feedback) }
    var counted by remember { mutableIntStateOf(0) }
    var form by remember { mutableIntStateOf(0) }
    var manual by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var reachedTarget by remember { mutableStateOf(false) }
    val amount = if (cameraOn) counted else manual

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (asking) continue
            elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            if (!cameraOn && exercise.mode == Mode.HOLD && !paused) manual++
        }
    }
    // A tick per rep and a stronger buzz at the target, so you can train without watching the screen.
    LaunchedEffect(amount) {
        if (amount <= 0) return@LaunchedEffect
        if (!reachedTarget && amount >= live.target) {
            reachedTarget = true
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        } else if (exercise.mode == Mode.REPS) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    fun result(): Workout? {
        if (amount <= 0) return null
        val hold = exercise.mode == Mode.HOLD
        return Workout(
            id = UUID.randomUUID().toString(),
            exercise = exercise,
            reps = if (hold) 0 else amount,
            seconds = if (hold) amount else elapsed.coerceAtLeast(1),
            form = if (cameraOn) form else 0,
            tracked = cameraOn,
            startedAt = startedAt,
        )
    }
    BackHandler { onDone(result()) }

    if (asking) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Allow camera access to check your form", color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
        return
    }

    if (!cameraOn) {
        ManualSession(live, amount, paused, elapsed, onAdd = { manual += it }, onPause = { paused = !paused }, onFinish = { onDone(result()) })
        return
    }

    var front by remember { mutableStateOf(true) }
    // Light status-bar icons over the dark camera feed; restore the theme's choice afterwards.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose { if (wasLight != null) bars?.isAppearanceLightStatusBars = wasLight }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PoseCamera(
            front = front,
            onFrame = { f ->
                frame = f
                val next = coach.onFrame(f, SystemClock.uptimeMillis())
                if (next != feedback) feedback = next
                if (coach.amount != counted) counted = coach.amount
                if (coach.formScore != form) form = coach.formScore
            },
            modifier = Modifier.fillMaxSize(),
        )
        SkeletonOverlay(frame = { frame }, color = { feedback.status.color() }, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(R.drawable.ic_close) { onDone(result()) }
                Text(
                    exercise.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                RoundButton(R.drawable.ic_sync) { front = !front }
            }
            Spacer(Modifier.height(12.dp))
            Cue(feedback, reachedTarget && feedback.status == Status.GOOD)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (exercise.mode == Mode.HOLD) clock(amount) else "$amount", style = MaterialTheme.typography.displaySmall)
                Text(
                    " / ${if (exercise.mode == Mode.HOLD) clock(live.target) else "${live.target}"} ${if (exercise.mode == Mode.HOLD) "" else exercise.unit}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp).weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(clock(elapsed), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                    Text(
                        if (amount > 0) "Form $form%" else "Form –",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Progress(amount.toFloat() / live.target)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Finish", onClick = { onDone(result()) })
        }
    }
}

@Composable
private fun Cue(feedback: Feedback, targetReached: Boolean) {
    val tone = feedback.status.color()
    val text = if (targetReached) "Target reached · keep going or finish" else feedback.cue
    Text(
        text,
        color = if (feedback.status == Status.FIX) Color(0xFF1B1300) else Color.White,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier.clip(CircleShape).background(tone).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Progress(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(Tone.accent))
    }
}

@Composable
private fun RoundButton(icon: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, tint = Color.White) }
}

@Composable
private fun ManualSession(
    live: WorkoutFlow.Live,
    amount: Int,
    paused: Boolean,
    elapsed: Int,
    onAdd: (Int) -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
) {
    val hold = live.exercise.mode == Mode.HOLD
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceVariant).clickable(onClick = onFinish),
                contentAlignment = Alignment.Center,
            ) { Glyph(R.drawable.ic_close) }
            Text(live.exercise.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(clock(elapsed), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (hold) clock(amount) else "$amount",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 88.sp, lineHeight = 92.sp),
        )
        Text(
            "of ${if (hold) clock(live.target) else "${live.target} reps"}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier.size(if (hold) 120.dp else 160.dp).clip(CircleShape).background(colors.primary).clickable { if (hold) onPause() else onAdd(1) },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(if (!hold) R.drawable.ic_plus else if (paused) R.drawable.ic_play else R.drawable.ic_pause, tint = colors.onPrimary, size = 40)
        }
        if (!hold) {
            TextButton(onClick = { if (amount > 0) onAdd(-1) }) { Text("Undo", color = colors.onSurfaceVariant) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (live.camera) "Camera permission is off, so this is manual mode. Manual workouts earn half points."
            else "Manual mode earns half points. Turn on the camera form check for full points.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Finish", onClick = onFinish)
    }
}

@Composable
fun SummaryScreen(workout: Workout, onSave: () -> Unit, onDiscard: () -> Unit) {
    BackHandler(onBack = onSave) // never lose a finished workout to a stray back press
    val hold = workout.exercise.mode == Mode.HOLD
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(48.dp).clip(CircleShape).background(Tone.good.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Glyph(R.drawable.ic_check, tint = Tone.good, size = 24)
        }
        Spacer(Modifier.height(16.dp))
        Text("Workout complete", style = MaterialTheme.typography.headlineSmall)
        Text(workout.exercise.title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(if (hold) "Held" else "Reps", if (hold) clock(workout.seconds) else "${workout.reps}", Modifier.weight(1f))
            StatTile("Form", if (workout.tracked) "${workout.form}%" else "–", Modifier.weight(1f))
            StatTile("Points", "+${workout.points}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Panel(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            SummaryLine("Duration", clock(workout.seconds))
            SummaryLine("Tracking", if (workout.tracked) "Camera form check" else "Manual (half points)")
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Save workout", onClick = onSave)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Discard", onClick = onDiscard, color = Tone.bad)
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
