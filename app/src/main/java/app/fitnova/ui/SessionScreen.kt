package app.fitnova.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.WorkoutFlow
import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode
import app.fitnova.domain.Workout
import app.fitnova.pose.Coach
import app.fitnova.pose.Feedback
import app.fitnova.pose.Frame
import app.fitnova.pose.PoseCamera
import app.fitnova.pose.SkeletonOverlay
import app.fitnova.pose.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** m:ss in ASCII digits whatever the phone's language, so the clock matches the counts. */
fun clock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private enum class Phase { REST, READY, COUNTDOWN, LIVE }

/** How this step is counted: by the camera, by hand, or not decided while the camera permission is open. */
private enum class Track { CAMERA, HAND, ASKING, REFUSED }

/**
 * One step's progress, saved whole across configuration changes. Times are SystemClock.elapsedRealtime(), which
 * keeps running through a recreation. Snapshot fields are what the screen shows; the rest is bookkeeping.
 */
@Stable
private class StepState(phase: Phase, restUntil: Long, restTotal: Int) {
    var phase by mutableStateOf(phase)
    var restUntil = restUntil
    var restTotal by mutableIntStateOf(restTotal)
    var restLeft by mutableIntStateOf(restTotal)
    var countdownUntil = 0L
    var digit by mutableIntStateOf(3)
    var liveAt = 0L // the clock's start; 0 = not started
    var startedAt = 0L // wall clock at liveAt, for the Workout
    var pausedAt by mutableLongStateOf(0L) // 0 = running
    var pausedFor = 0L
    var seconds by mutableIntStateOf(0) // the clock
    var amount by mutableIntStateOf(0) // reps, or seconds held
    var form by mutableIntStateOf(0)
    var clean = 0 // camera reps in good form
    var holdMs = 0L // camera holds: time in position…
    var goodMs = 0L // …and the part of it in good form
    var lastAdd = 1 // what Undo takes back
    var reached by mutableStateOf(false)
    var restSaid = false
    var nextSaid = false
    var feedback by mutableStateOf(Feedback(Status.SEARCHING, ""))
    var seenSince = 0L

    val paused get() = pausedAt != 0L

    fun elapsedMs(now: Long) = if (liveAt == 0L) 0L else ((if (paused) pausedAt else now) - liveAt - pausedFor).coerceAtLeast(0L)

    companion object {
        val Saver = listSaver<StepState, Any>(
            save = {
                listOf(
                    it.phase.ordinal, it.restUntil, it.restTotal, it.countdownUntil, it.liveAt, it.startedAt, it.pausedAt,
                    it.pausedFor, it.amount, it.form, it.clean, it.holdMs, it.goodMs, it.lastAdd, it.reached, it.restSaid, it.nextSaid,
                )
            },
            restore = { v ->
                StepState(Phase.entries[v[0] as Int], v[1] as Long, v[2] as Int).apply {
                    countdownUntil = v[3] as Long
                    liveAt = v[4] as Long
                    startedAt = v[5] as Long
                    pausedAt = v[6] as Long
                    pausedFor = v[7] as Long
                    amount = v[8] as Int
                    form = v[9] as Int
                    clean = v[10] as Int
                    holdMs = v[11] as Long
                    goodMs = v[12] as Long
                    lastAdd = v[13] as Int
                    reached = v[14] as Boolean
                    restSaid = v[15] as Boolean
                    nextSaid = v[16] as Boolean
                }
            },
        )
    }
}

/** Counts from before a configuration change; the new Coach adds to them. */
private class Bank(val reps: Int, val clean: Int, val holdMs: Long, val goodMs: Long)

private fun percent(part: Long, whole: Long) = if (whole <= 0L) 0 else (part * 100 / whole).toInt()

private fun Context.hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * A live workout step, always dark. In a routine this screen stays up from step to step (each step gets fresh
 * state, keyed by [live]), so the voice coach, the camera side and a "count by hand" choice carry over.
 */
@Composable
fun SessionScreen(live: WorkoutFlow.Live, onDone: (Workout?) -> Unit, vm: AppViewModel = viewModel()) {
    WorkoutTheme {
        WorkoutWindow()
        val voice = rememberVoice(vm.voice)
        var front by rememberSaveable { mutableStateOf(true) }
        var byHand by rememberSaveable { mutableStateOf(false) }
        key(live) {
            Step(
                live, voice, vm,
                front = front,
                onFlip = { front = !front },
                byHand = byHand,
                onByHand = { byHand = true },
                onNext = onDone,
                onEnd = { result -> if (live.routine != null) vm.endRoutine(result) else onDone(result) },
            )
        }
    }
}

@Composable
private fun Step(
    live: WorkoutFlow.Live,
    voice: Voice?,
    vm: AppViewModel,
    front: Boolean,
    onFlip: () -> Unit,
    byHand: Boolean,
    onByHand: () -> Unit,
    onNext: (Workout?) -> Unit,
    onEnd: (Workout?) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val exercise = live.exercise
    val hold = exercise.mode == Mode.HOLD
    val target = live.target
    val routine = live.routine
    val s = rememberSaveable(saver = StepState.Saver) {
        StepState(if (live.rest > 0) Phase.REST else Phase.READY, SystemClock.elapsedRealtime() + live.rest * 1000L, live.rest)
    }

    // Camera: wanted for tracked moves unless refused; asked for after any rest.
    val wantsCamera = live.camera && exercise.trackable && !byHand
    var granted by remember { mutableStateOf(context.hasCamera()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var permanent by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
        // Refused and no rationale to show = "don't ask again": only Settings can turn it back on.
        permanent = !ok && context.hostActivity()?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true
    }
    LifecycleResumeEffect(Unit) {
        granted = context.hasCamera() // back from Settings
        onPauseOrDispose { }
    }
    val resting = s.phase == Phase.REST
    LaunchedEffect(wantsCamera, resting) {
        if (wantsCamera && !resting && !granted && !asked) permission.launch(Manifest.permission.CAMERA)
    }
    val track = when {
        !wantsCamera -> Track.HAND
        granted -> Track.CAMERA
        !asked -> Track.ASKING
        else -> Track.REFUSED
    }

    val coach = remember { Coach(exercise) }
    val probe = remember { Coach(exercise) } // get ready: are the joints the coach needs in view?
    val bank = remember { Bank(s.amount, s.clean, s.holdMs, s.goodMs) }
    val frame = remember { mutableStateOf<Frame?>(null) }
    val gate = remember { CueGate() }
    val bump = remember { Animatable(1f) }
    var finished by remember { mutableStateOf(false) }
    var askEnd by rememberSaveable { mutableStateOf(false) }

    fun counted(from: Int, to: Int) {
        s.amount = to
        if (to <= from) return
        if (!hold) {
            scope.launch {
                bump.snapTo(1f)
                bump.animateTo(1.08f, tween(70))
                bump.animateTo(1f, tween(110))
            }
        }
        if (!s.reached && to >= target) {
            s.reached = true
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        } else if (!hold) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        if (hold) voice?.held(to, target) else voice?.rep(to, target, from)
    }

    fun goLive(now: Long) {
        s.phase = Phase.LIVE
        s.liveAt = now
        s.startedAt = System.currentTimeMillis()
        s.feedback = coach.feedback
    }

    fun countdown(now: Long) {
        s.countdownUntil = now + 3000
        s.digit = 3
        s.phase = Phase.COUNTDOWN
        voice?.countdown(3)
    }

    fun togglePause() {
        val now = SystemClock.elapsedRealtime()
        if (s.paused) {
            s.pausedFor += now - s.pausedAt
            s.pausedAt = 0L
        } else if (s.liveAt != 0L) {
            s.pausedAt = now
        }
    }

    fun onFrame(f: Frame) {
        frame.value = f
        val now = SystemClock.elapsedRealtime()
        when (s.phase) {
            Phase.READY -> {
                val seen = probe.onFrame(f, now).status != Status.SEARCHING
                val status = if (seen) Status.GOOD else Status.SEARCHING
                if (s.feedback.status != status) s.feedback = Feedback(status, "")
                if (!seen) s.seenSince = 0L else if (s.seenSince == 0L) s.seenSince = now
                if (seen && now - s.seenSince >= 1000) countdown(now)
            }
            Phase.LIVE -> if (!s.paused) {
                val fb = coach.onFrame(f, now)
                if (fb != s.feedback) s.feedback = fb
                gate.update(fb.cue.takeIf { fb.status == Status.FIX }, now)?.let { voice?.cue(it) }
                if (hold) {
                    // Time in position counts; form is the good share of it, so a hold is only marked down once.
                    s.holdMs = bank.holdMs + coach.holdMs
                    s.goodMs = bank.goodMs + coach.goodHoldMs
                    val held = (s.holdMs / 1000).toInt()
                    if (held != s.amount) {
                        s.form = percent(s.goodMs, s.holdMs)
                        counted(s.amount, held)
                    }
                } else {
                    val reps = bank.reps + coach.reps
                    if (reps != s.amount) {
                        s.clean = bank.clean + (coach.formScore * coach.reps + 50) / 100
                        s.form = percent(s.clean.toLong(), reps.toLong())
                        counted(s.amount, reps)
                    }
                }
            }
            else -> Unit
        }
    }

    // One ticker for the rest countdown, the 3-2-1 and the clock. Restarted when the voice or the mode changes.
    LaunchedEffect(s, voice, track) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            when (s.phase) {
                Phase.REST -> {
                    val left = ((s.restUntil - now + 999) / 1000).toInt().coerceAtLeast(0)
                    s.restLeft = left
                    if (!s.restSaid) {
                        s.restSaid = true
                        voice?.say("Rest ${s.restTotal} seconds")
                    }
                    if (left <= 5 && !s.nextSaid) {
                        s.nextSaid = true
                        voice?.say("Next: ${exercise.title}")
                    }
                    if (left == 0) s.phase = Phase.READY
                }
                Phase.COUNTDOWN -> {
                    val d = ((s.countdownUntil - now + 999) / 1000).toInt().coerceAtLeast(0)
                    if (d != s.digit) {
                        s.digit = d
                        voice?.countdown(d)
                    }
                    if (d == 0) goLive(now)
                }
                Phase.LIVE -> {
                    s.seconds = (s.elapsedMs(now) / 1000).toInt()
                    if (hold && track == Track.HAND && s.seconds != s.amount) counted(s.amount, s.seconds)
                }
                Phase.READY -> if (track == Track.HAND && !hold) s.phase = Phase.LIVE // reps by hand: the clock starts at the first tap
            }
            delay(200)
        }
    }

    fun result(): Workout? {
        if (s.amount <= 0) return null
        val tracked = track == Track.CAMERA
        return Workout(
            id = UUID.randomUUID().toString(),
            exercise = exercise,
            reps = if (hold) 0 else s.amount,
            seconds = if (hold) s.amount else (s.elapsedMs(SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(1),
            form = if (tracked) s.form else 0,
            tracked = tracked,
            startedAt = if (s.startedAt > 0L) s.startedAt else System.currentTimeMillis(),
        )
    }

    // Once per step: a double tap must not save twice or skip the next step.
    fun next() {
        if (finished) return
        finished = true
        onNext(result())
    }

    fun end() {
        if (finished) return
        finished = true
        onEnd(result())
    }

    BackHandler { askEnd = true }
    val endLabel = if (routine != null) "End routine" else "End workout"
    val progress = routine?.let { "${live.step + 1}/${it.steps.size}" }
    val nextLabel = if (routine != null && live.step < routine.steps.size - 1) "Next" else "Finish"
    val top: @Composable (Boolean) -> Unit = { overVideo ->
        SessionTopBar(if (s.phase == Phase.REST) routine?.title ?: exercise.title else exercise.title, progress, endLabel, { askEnd = true }, overVideo) {
            VoiceButton(vm.voice, { vm.changeVoice(!vm.voice) }, overVideo)
            if (overVideo) IconCircleButton(R.drawable.ic_camera_rotate, "Switch camera", onFlip, container = OverVideo, tint = Color.White)
        }
    }

    when {
        s.phase == Phase.REST -> RestScreen(
            exercise, target, wantsCamera,
            left = { s.restLeft },
            total = { s.restTotal },
            onMore = {
                s.restUntil += 10_000
                s.restTotal += 10
                s.restLeft += 10
                if (s.restLeft > 5) s.nextSaid = false
            },
            onSkip = { s.phase = Phase.READY },
            top = { top(false) },
        )
        track == Track.ASKING -> AskingCamera()
        track == Track.REFUSED -> CameraOff(
            permanent,
            onAllow = {
                if (permanent) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }
                } else {
                    permission.launch(Manifest.permission.CAMERA)
                }
            },
            onManual = onByHand,
            top = { top(false) },
        )
        track == Track.CAMERA -> CameraStep(
            s, exercise, target, front, frame, ::onFrame, { bump.value }, nextLabel,
            onNext = ::next,
            onPause = ::togglePause,
            onByHand = onByHand,
            top = { top(true) },
        )
        else -> HandStep(
            s, hold, target, { bump.value }, nextLabel,
            onAdd = { n ->
                if (s.liveAt == 0L) goLive(SystemClock.elapsedRealtime())
                if (s.paused) togglePause()
                s.lastAdd = n
                counted(s.amount, s.amount + n)
            },
            onUndo = {
                s.amount = (s.amount - s.lastAdd).coerceAtLeast(0)
                s.lastAdd = 1
            },
            onStart = { countdown(SystemClock.elapsedRealtime()) },
            onPause = ::togglePause,
            onNext = ::next,
            top = { top(false) },
        )
    }

    if (askEnd) {
        ConfirmDialog(
            if (routine != null) "End routine?" else "End workout?",
            text = if (s.amount > 0) "What you did is saved." else null,
            confirm = "End",
            onConfirm = ::end,
            onDismiss = { askEnd = false },
        )
    }
}

/** Camera mode: preview with the skeleton, get-ready card, 3-2-1, then the cue pill and a compact translucent panel. */
@Composable
private fun CameraStep(
    s: StepState,
    exercise: Exercise,
    target: Int,
    front: Boolean,
    frame: State<Frame?>,
    onFrame: (Frame) -> Unit,
    bump: () -> Float,
    nextLabel: String,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onByHand: () -> Unit,
    top: @Composable () -> Unit,
) {
    val hold = exercise.mode == Mode.HOLD
    Box(Modifier.fillMaxSize()) {
        // Paused: the camera is released (no frames, no heat) until resume.
        if (s.paused) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text("Paused", style = MaterialTheme.typography.headlineMedium)
            }
        } else {
            PoseCamera(front, onFrame, Modifier.fillMaxSize())
            SkeletonOverlay(frame = { frame.value }, color = { s.feedback.status.cueColor() }, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            top()
            if (s.phase == Phase.LIVE && !s.paused) {
                Spacer(Modifier.height(12.dp))
                CuePill({ s.feedback }, { s.reached })
            }
        }
        val bottom = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
        when (s.phase) {
            Phase.READY -> Column(bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                ReadyCard({ s.feedback.status == Status.GOOD }, exercise)
                Spacer(Modifier.height(8.dp))
                // The way out when the camera can't see well enough (dark room, no space).
                Text(
                    "Count by hand",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(OverVideo)
                        .clickable(role = Role.Button, onClick = onByHand)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            Phase.COUNTDOWN -> CountdownDigit({ s.digit }, Modifier.align(Alignment.Center))
            else -> LivePanel(s, hold, target, bump, nextLabel, onNext, onPause, bottom)
        }
    }
}

@Composable
private fun LivePanel(
    s: StepState,
    hold: Boolean,
    target: Int,
    bump: () -> Float,
    nextLabel: String,
    onNext: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer.copy(alpha = 0.88f), MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f)) {
                LiveCount({ s.amount }, hold, bump, Modifier.weight(1f, fill = false).alignByBaseline())
                Text(
                    "/${if (hold) clock(target) else "$target"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline().padding(start = 6.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LiveClock({ s.seconds })
                LiveForm({ s.form.takeIf { s.amount > 0 } })
            }
        }
        Spacer(Modifier.height(8.dp))
        LiveProgress({ s.amount }, target)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PauseToggle({ s.paused }, onPause)
            PrimaryButton(nextLabel, onNext, Modifier.weight(1f))
        }
    }
}

/**
 * Counting by hand: giant count, +1 (and +5 for high counts) with Undo, or a start/pause timer for holds.
 * The clock runs only once the first rep is tapped (reps) or after the 3-2-1 (holds).
 */
@Composable
private fun HandStep(
    s: StepState,
    hold: Boolean,
    target: Int,
    bump: () -> Float,
    nextLabel: String,
    onAdd: (Int) -> Unit,
    onUndo: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    top: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        top()
        Spacer(Modifier.weight(1f))
        if (s.phase == Phase.COUNTDOWN) {
            CountdownDigit({ s.digit })
        } else {
            LiveCount({ s.amount }, hold, bump)
            Text(
                "of ${if (hold) clock(target) else "$target reps"}",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            LiveProgress({ s.amount }, target, Modifier.width(200.dp))
        }
        Spacer(Modifier.weight(1f))
        if (hold) {
            HoldButton(s, onStart, onPause)
        } else {
            // 64 + 128 + 64 and two gaps fit a 320 dp phone; the side slots keep +1 centred.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    // Arrow-clockwise mirrored = the usual undo arrow.
                    IconCircleButton(
                        R.drawable.ic_refresh, "Undo", onUndo,
                        enabled = s.amount > 0,
                        modifier = Modifier.graphicsLayer { scaleX = -1f },
                    )
                }
                RepButton("+1", "Add a rep", 128.dp, primary = true) { onAdd(1) }
                Box(Modifier.size(64.dp)) {
                    if (target >= 20) RepButton("+5", "Add 5 reps", 64.dp, primary = false) { onAdd(5) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hold) LiveClock({ s.seconds })
            Tag("By hand · ½ pts")
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hold) PauseToggle({ s.paused }, onPause, enabled = s.amount > 0)
            PrimaryButton(nextLabel, onNext, Modifier.weight(1f))
        }
    }
}

/** Holds by hand: Start (then 3-2-1), then pause and resume. */
@Composable
private fun HoldButton(s: StepState, onStart: () -> Unit, onPause: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ready = s.phase == Phase.READY
    val paused = s.paused
    val label = when {
        ready -> "Start"
        paused -> "Resume"
        else -> "Pause"
    }
    Box(
        Modifier
            .size(136.dp)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(enabled = s.phase != Phase.COUNTDOWN, role = Role.Button) { if (ready) onStart() else onPause() }
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(if (ready || paused) R.drawable.ic_play_fill else R.drawable.ic_pause_fill), null, Modifier.size(56.dp), tint = colors.onPrimary)
    }
}
