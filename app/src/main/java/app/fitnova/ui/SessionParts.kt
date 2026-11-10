package app.fitnova.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.WorkoutFlow
import app.fitnova.domain.Badge
import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode
import app.fitnova.domain.Workout
import app.fitnova.pose.Feedback
import app.fitnova.pose.Status

// Pieces shared by the live session, the rest screen and the summaries. Workout mode is always dark.

/** Control fill over the camera feed. */
internal val OverVideo = Color.Black.copy(alpha = 0.45f)

internal fun Context.hostActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Workout mode is always dark. These screens sit on the app's own (maybe light) surface, so the content colour
 * is set here too, or default-coloured text would come out dark on dark.
 */
@Composable
internal fun WorkoutTheme(content: @Composable () -> Unit) {
    FitNovaTheme(dark = true) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

/** Light bar icons over the dark workout screens, and (while [keepOn]) the screen stays awake. */
@Composable
internal fun WorkoutWindow(keepOn: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, keepOn) {
        view.keepScreenOn = keepOn
        val bars = view.context.hostActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = bars?.isAppearanceLightStatusBars
        val nav = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (keepOn) view.keepScreenOn = false
            if (bars != null && status != null && nav != null) {
                bars.isAppearanceLightStatusBars = status
                bars.isAppearanceLightNavigationBars = nav
            }
        }
    }
}

/** Close, title (with "2/6" in a routine) and [actions]. Over the camera the controls sit on dark translucent discs. */
@Composable
internal fun SessionTopBar(
    title: String,
    progress: String?,
    closeLabel: String,
    onClose: () -> Unit,
    overVideo: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconCircleButton(
            R.drawable.ic_close, closeLabel, onClose,
            container = if (overVideo) OverVideo else colors.surfaceVariant,
            tint = if (overVideo) Color.White else colors.onSurface,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            Row(
                (if (overVideo) Modifier.background(OverVideo, CircleShape) else Modifier).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (overVideo) Color.White else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (progress != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(progress, style = MaterialTheme.typography.labelLarge, color = if (overVideo) Color.White.copy(alpha = 0.8f) else colors.onSurfaceVariant)
                }
            }
        }
        actions()
    }
}

/** Mute / unmute the voice coach. */
@Composable
internal fun VoiceButton(on: Boolean, onToggle: () -> Unit, overVideo: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    IconCircleButton(
        if (on) R.drawable.ic_speaker else R.drawable.ic_speaker_off,
        if (on) "Mute voice coach" else "Turn on voice coach",
        onToggle,
        container = if (overVideo) OverVideo else colors.surfaceVariant,
        tint = if (overVideo) Color.White else colors.onSurface,
    )
}

/**
 * The giant count. [bump] (1 → 1.08 → 1 per rep) is read in the layer phase, and the Nova dot lights with it.
 * Shrinks to fit narrow screens and large font sizes instead of wrapping.
 */
@Composable
internal fun BigCount(text: String, bump: () -> Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier) {
        BasicText(
            text,
            Modifier
                .weight(1f, fill = false)
                .graphicsLayer {
                    val s = bump()
                    scaleX = s
                    scaleY = s
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.displayLarge.copy(color = colors.onSurface, lineHeight = 1.em),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 96.sp),
        )
        Box(
            Modifier
                .padding(start = 6.dp, top = 14.dp)
                .size(10.dp)
                .graphicsLayer {
                    alpha = ((bump() - 1f) / 0.08f).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .background(colors.primary, CircleShape),
        )
    }
}

internal fun Status.cueColor() = when (this) {
    Status.GOOD -> Nova.LiveGood
    Status.FIX -> Nova.LiveWarn
    Status.SEARCHING -> Nova.LiveIdle
}

private fun Status.icon() = when (this) {
    Status.GOOD -> R.drawable.ic_check_circle
    Status.FIX -> R.drawable.ic_warning
    Status.SEARCHING -> R.drawable.ic_eye
}

/** Two or three words, big enough to read from the floor; the voice coach speaks the full sentence. */
internal fun shortCue(cue: String) = ShortCues[cue] ?: cue

private val ShortCues = mapOf(
    "Good form" to "Good form",
    "Step back so your whole body is visible" to "Step back",
    "Show your upper body and arms" to "Show arms",
    "Get into position, whole body in frame" to "Get in position",
    "Hips sagging, tighten your core" to "Hips up",
    "Hips too high, straighten your body" to "Hips down",
    "Chest up, keep your back straighter" to "Chest up",
    "Go lower, chest toward the floor" to "Go lower",
    "Slide lower, thighs parallel to the floor" to "Lower",
    "Too low, come up a little" to "Come up",
    "Keep your back flat against the wall" to "Back to wall",
    "Keep your elbows by your sides" to "Elbows in",
    "Brace your core, don't lean back" to "Don't lean",
    "Keep your legs straight" to "Legs straight",
    "Keep your torso upright" to "Torso up",
    "Lower until your elbows reach 90°" to "Elbows 90°",
    "Lower the weights to shoulder level" to "Lower to shoulders",
    "Stop at shoulder height" to "Shoulder height",
    "Squat deeper" to "Deeper",
    "Lower your back knee more" to "Knee lower",
    "Curl all the way up" to "Curl higher",
    "Raise your arms to shoulder height" to "Arms higher",
    "Raise your arms higher" to "Arms higher",
    "Lift your hips higher" to "Hips higher",
    "Sit all the way up" to "Sit up fully",
    "Raise your legs higher" to "Legs higher",
    "Drive your knees higher" to "Knees higher",
)

/** The coach's cue on its live colour with an icon (never colour alone). Reads its state itself, so only it recomposes. */
@Composable
internal fun CuePill(feedback: () -> Feedback, reached: () -> Boolean, modifier: Modifier = Modifier) {
    val f = feedback()
    val text = if (reached() && f.status == Status.GOOD) "Target! Keep going" else shortCue(f.cue)
    Row(
        modifier
            .background(f.status.cueColor(), CircleShape)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(f.status.icon()), null, Modifier.size(24.dp), tint = Nova.OnLive)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.headlineSmall, color = Nova.OnLive, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Get-ready card: the pose to take (stick figure) and where the phone goes, until the joints the coach needs are
 * in view; then "Hold still" before the 3-2-1.
 */
@Composable
internal fun ReadyCard(seen: () -> Boolean, exercise: Exercise, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ok = seen()
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer.copy(alpha = 0.88f), MaterialTheme.shapes.large)
            .padding(20.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseTile(exercise, 56)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchDot(ok)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            ok -> "Hold still"
                            exercise.mode == Mode.HOLD -> "Get in position"
                            else -> "Step back"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Text("Whole body in view", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (exercise.cameraTip.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_camera, size = 18, tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(exercise.cameraTip, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Steady green once seen; while searching it pulses (alpha only, the one allowed loop), and stops once seen. */
@Composable
private fun SearchDot(seen: Boolean) {
    if (seen) {
        Box(Modifier.size(14.dp).background(Nova.LiveGood, CircleShape))
    } else {
        val pulse = rememberInfiniteTransition(label = "search")
        val alpha = pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha")
        Box(
            Modifier
                .size(14.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .background(Nova.LiveIdle, CircleShape),
        )
    }
}

/** 3-2-1 in a dark disc. */
@Composable
internal fun CountdownDigit(digit: () -> Int, modifier: Modifier = Modifier) {
    Box(modifier.size(180.dp).background(OverVideo, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            "${digit().coerceAtLeast(1)}",
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Shown while the system asks for the camera. */
@Composable
internal fun AskingCamera() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(R.drawable.ic_camera_duo, MaterialTheme.colorScheme.primary, 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("Allow the camera", style = MaterialTheme.typography.headlineSmall)
    }
}

/**
 * Camera permission was refused. [permanent] = the system won't ask again, so Settings is the way back
 * (the screen re-checks on return). Either way the move can go on counted by hand.
 */
@Composable
internal fun CameraOff(permanent: Boolean, onAllow: () -> Unit, onManual: () -> Unit, top: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        top()
        Spacer(Modifier.weight(1f))
        IconTile(R.drawable.ic_camera_off_duo, Nova.colors.warn, 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("Camera is off", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            if (permanent) "Allow it in Settings for full points" else "Counting by hand gives half points",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (permanent) "Open settings" else "Allow camera", onAllow)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Count by hand", onManual)
    }
}

/** "15 reps · 92%", "0:45 · 80%", or "· by hand" for manual counts. */
internal fun Workout.summaryLine(): String {
    val amount = if (exercise.mode == Mode.HOLD) clock(seconds) else "$reps reps"
    return if (tracked) "$amount · $form%" else "$amount · by hand"
}

/**
 * After a workout. Everything shown is already saved: Done (or back) closes, Delete asks first.
 * A routine's summary lists its steps.
 */
@Composable
fun SummaryScreen(workout: Workout, onSave: () -> Unit, onDiscard: () -> Unit, vm: AppViewModel = viewModel()) {
    val done = (vm.flow as? WorkoutFlow.Done)?.takeIf { it.workout.id == workout.id }
    WorkoutTheme {
        WorkoutWindow(keepOn = false)
        BackHandler(onBack = onSave)
        if (done?.routine != null) {
            RoutineSummary(done, onSave, onDiscard)
        } else {
            WorkoutSummary(workout, done?.target ?: 0, done?.badge, onSave, onDiscard)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutSummary(w: Workout, target: Int, badge: Badge?, onDone: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val hold = w.exercise.mode == Mode.HOLD
    val amount = if (hold) w.seconds else w.reps
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(20.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Illustration(Illu.CELEBRATE, height = 132.dp)
            Spacer(Modifier.height(16.dp))
            Text(w.exercise.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            PointsHeadline(w.points)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    target > 0 && amount >= target -> Tag("Target hit", tone = Nova.colors.good, dot = true)
                    target > 0 -> Tag(if (hold) "${clock(amount)}/${clock(target)}" else "$amount/$target reps")
                }
                Tag(if (w.tracked) "Camera" else "By hand · ½ pts")
                badge?.let { Tag("${it.emoji} ${it.title}", tone = colors.primary) }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(if (hold) "Held" else "Reps", if (hold) clock(w.seconds) else "${w.reps}", Modifier.weight(1f))
                StatTile("Form", if (w.tracked) "${w.form}%" else "–", Modifier.weight(1f))
                if (!hold) StatTile("Time", clock(w.seconds), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Done", onDone)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Delete", { confirm = true }, color = Nova.colors.bad)
    }
    if (confirm) {
        ConfirmDialog("Delete workout?", confirm = "Delete", destructive = true, onConfirm = onDelete, onDismiss = { confirm = false })
    }
}

/** "+12 pts", the summary's one big number. */
@Composable
internal fun PointsHeadline(points: Int) {
    Row {
        Text("+$points", style = MaterialTheme.typography.displayMedium, modifier = Modifier.alignByBaseline())
        Text(
            "pts",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline().padding(start = 6.dp),
        )
    }
}

@Composable
internal fun RepButton(text: String, label: String, size: Dp, primary: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (primary) colors.primary else colors.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (primary) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            color = if (primary) colors.onPrimary else colors.onSurface,
        )
    }
}

@Composable
internal fun PauseToggle(paused: () -> Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val p = paused()
    IconCircleButton(if (p) R.drawable.ic_play else R.drawable.ic_pause, if (p) "Resume" else "Pause", onClick, enabled = enabled)
}

// Leaf readers: each one recomposes alone when its number changes, never the whole camera screen.

@Composable
internal fun LiveCount(amount: () -> Int, hold: Boolean, bump: () -> Float, modifier: Modifier = Modifier) {
    val a = amount()
    BigCount(if (hold) clock(a) else "$a", bump, modifier)
}

@Composable
internal fun LiveClock(seconds: () -> Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(R.drawable.ic_timer, size = 16, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(clock(seconds()), style = MaterialTheme.typography.labelLarge)
    }
}

/** Form score; null (nothing counted yet) shows a dash. */
@Composable
internal fun LiveForm(form: () -> Int?) {
    val text = form()?.let { "$it%" } ?: "–"
    Row(Modifier.clearAndSetSemantics { contentDescription = "Form $text" }, verticalAlignment = Alignment.CenterVertically) {
        Glyph(R.drawable.ic_target, size = 16, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun LiveProgress(amount: () -> Int, target: Int, modifier: Modifier = Modifier.fillMaxWidth()) {
    Meter(amount() / target.coerceAtLeast(1).toFloat(), Nova.colors.points, modifier)
}
