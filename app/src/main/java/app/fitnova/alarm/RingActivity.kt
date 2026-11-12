package app.fitnova.alarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.fitnova.Graph
import app.fitnova.R
import app.fitnova.data.SyncJob
import app.fitnova.domain.SUMS_TO_SOLVE
import app.fitnova.domain.Sum
import app.fitnova.domain.SumChallenge
import app.fitnova.domain.Workout
import app.fitnova.domain.hm
import app.fitnova.pose.Coach
import app.fitnova.pose.Feedback
import app.fitnova.pose.Frame
import app.fitnova.pose.PoseCamera
import app.fitnova.pose.SkeletonOverlay
import app.fitnova.pose.Status
import app.fitnova.ui.Emoji
import app.fitnova.ui.EmojiTile
import app.fitnova.ui.ExerciseTile
import app.fitnova.ui.FitNovaTheme
import app.fitnova.ui.Glyph
import app.fitnova.ui.Meter
import app.fitnova.ui.Nova
import app.fitnova.ui.Panel
import app.fitnova.ui.PrimaryButton
import app.fitnova.ui.SecondaryButton
import app.fitnova.ui.StatTile
import app.fitnova.ui.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The alarm's challenge, shown over the lock screen: always dark, one big clock, one primary button. Opened only by
 * the ring notification's full-screen intent (or by tapping it, or by opening the app while it rings), never by a
 * background start. Shows only the time, label and challenge: nothing personal over the keyguard.
 */
class RingActivity : ComponentActivity() {
    private var morning by mutableStateOf<Morning?>(null)
    private var finishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated after the challenge was done: ringing is null again, so carrying on would ring this alarm twice.
        if (savedInstanceState?.getBoolean("done") == true) return finish()
        val ring = Ring.from(intent) ?: RingService.ringing.value ?: return finish()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(0xFF0B0C0F.toInt()))
        volumeControlStream = AudioManager.STREAM_ALARM
        // Opened from a notification whose service never started: we're visible now, so ring from here.
        if (RingService.ringing.value == null) RingService.start(this, ring)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)

        // Leave when the ringing stops elsewhere: snoozed from the notification, or the 15-minute cap.
        lifecycleScope.launch {
            var seen = false
            RingService.ringing.collect { now ->
                if (now != null) seen = true else if (seen && !finishing) finish()
            }
        }

        val camera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val onSnooze = if (ring.snoozes > 0 && AlarmScheduler.canExact(this)) ({ snooze(ring) }) else null
        setContent {
            FitNovaTheme(dark = true) {
                // The Surface also sets the content colour that plain Text falls back to.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val m = morning
                    if (m != null) GoodMorning(m, onClose = ::finish) else RingScreen(ring, camera, ::calm, onSnooze, onDone = { done(ring, it) })
                }
            }
        }
    }

    // singleTask: a new alarm while the last one's "Good morning" is still up starts over for the new one.
    // (A tap on the same ring's notification mid-challenge must not reset it, so only after the last one is done.)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (morning != null && Ring.from(intent) != null) {
            finishing = false // the new ring isn't done: the recreated screen must take it on, not close
            setIntent(intent)
            recreate()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("done", finishing)
    }

    // Volume keys must not turn the alarm down while the challenge is up.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        return (volume && morning == null) || super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        // Once done, a ring is a new one and "Good morning" isn't its screen: leave its loud alert alone.
        if (!finishing) RingService.screenVisible(this, true)
    }

    override fun onPause() {
        super.onPause()
        // Not when recreated: the new instance resumes at once, so the loud heads-up would only flash over it.
        if (!finishing && !isChangingConfigurations) RingService.screenVisible(this, false)
    }

    /** Start, then every rep or key: keeps the ring calm, which RingService ends after a minute without any. */
    private fun calm() {
        startService(Intent(this, RingService::class.java).setAction(RingService.CALM))
    }

    private fun snooze(ring: Ring) {
        RingService.snooze(this, ring)
        finish()
    }

    /** Challenge done: stop ringing, end the night, and keep the reps as a camera-tracked workout. */
    private fun done(ring: Ring, workout: Workout?) {
        if (finishing) return
        finishing = true
        RingService.end(this)
        val app = applicationContext
        alarmScope.launch {
            val night = if (ring.id == AlarmScheduler.TEST_ID) null else Graph.sleep.wakeUp("alarm")
            if (workout != null) {
                Graph.store.save(listOf(workout)) // wake-up reps count for points, streaks and the leaderboard
                if (Graph.session.signedIn) SyncJob.schedule(app)
            }
            withContext(Dispatchers.Main) { morning = Morning(night?.minutes, workout?.points) }
        }
    }
}

private class Morning(val slept: Int?, val points: Int?)

private enum class Stage { INTRO, POSE, MATHS }

/** No new rep for this long (dark room, covered camera, body out of frame): offer the sums. */
private const val STUCK_MS = 45_000L

private val H24 = DateTimeFormatter.ofPattern("H:mm")
private val H12 = DateTimeFormatter.ofPattern("h:mm")
private val AM_PM = DateTimeFormatter.ofPattern("a")

@Composable
private fun RingScreen(ring: Ring, camera: Boolean, onProgress: () -> Unit, onSnooze: (() -> Unit)?, onDone: (Workout?) -> Unit) {
    var stage by rememberSaveable { mutableStateOf(Stage.INTRO) }
    var partial by remember { mutableStateOf<Workout?>(null) }
    BackHandler {} // the alarm stops only through its challenge
    Box(Modifier.fillMaxSize()) {
        when (stage) {
            Stage.INTRO -> Intro(ring, camera, onStart = { onProgress(); stage = if (camera) Stage.POSE else Stage.MATHS }, onSnooze)
            Stage.POSE -> PoseChallenge(ring, onRep = onProgress, onDone = onDone, onStuck = { onProgress(); partial = it; stage = Stage.MATHS })
            Stage.MATHS -> MathsChallenge(onKey = onProgress, onDone = { onDone(partial) })
        }
    }
}

@Composable
private fun Intro(ring: Ring, camera: Boolean, onStart: () -> Unit, onSnooze: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        RingingAlarm()
        Spacer(Modifier.height(12.dp))
        Clock()
        Text(ring.label.ifBlank { "Wake up" }, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (camera) ExerciseTile(ring.exercise, 56) else EmojiTile(R.drawable.emoji_brain, size = 56.dp)
                Column {
                    Text(
                        if (camera) "Do ${ring.reps} ${ring.exercise.title.lowercase()}" else "Solve $SUMS_TO_SOLVE sums",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text("to stop the alarm", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Start", onStart)
        if (onSnooze != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Snooze 5 min · ${ring.snoozes} left", onSnooze)
        }
    }
}

/** The ringing screen is one of the two places allowed an endless animation: a shaking alarm clock. */
@Composable
private fun RingingAlarm() {
    val shake = rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1400
                -14f at 70
                14f at 140
                -14f at 210
                14f at 280
                -10f at 350
                10f at 420
                0f at 490
            },
        ),
        label = "shake",
    )
    Emoji(R.drawable.emoji_alarm, 96.dp, Modifier.graphicsLayer { rotationZ = shake.value })
}

/** The current time, in the user's 12/24-hour setting; recomposes once a minute. */
@Composable
private fun Clock() {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
    Row {
        Text(t.format(if (is24) H24 else H12), style = MaterialTheme.typography.displayLarge, maxLines = 1, modifier = Modifier.alignByBaseline())
        if (!is24) {
            Text(
                t.format(AM_PM),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline().padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun PoseChallenge(ring: Ring, onRep: () -> Unit, onDone: (Workout) -> Unit, onStuck: (Workout?) -> Unit) {
    val coach = remember { Coach(ring.exercise) }
    val startedAt = remember { System.currentTimeMillis() }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var feedback by remember { mutableStateOf(coach.feedback) }
    var reps by remember { mutableIntStateOf(0) }
    var stuck by remember { mutableStateOf(false) }
    fun workout() = Workout(
        UUID.randomUUID().toString(), ring.exercise, reps,
        ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1), coach.formScore, tracked = true, startedAt = startedAt,
    )
    LaunchedEffect(reps) {
        if (reps >= ring.reps) return@LaunchedEffect onDone(workout())
        if (reps > 0) onRep()
        stuck = false
        delay(STUCK_MS)
        stuck = true
    }
    FullBrightness()
    // The white frame at full brightness doubles as a fill light in a dark bedroom.
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp).clip(MaterialTheme.shapes.extraLarge)) {
            PoseCamera(
                front = true,
                onFrame = { f ->
                    frame = f
                    feedback = coach.onFrame(f, SystemClock.uptimeMillis())
                    if (coach.reps != reps) reps = coach.reps
                },
                modifier = Modifier.fillMaxSize(),
            )
            SkeletonOverlay(frame = { frame }, color = { live(feedback.status) }, modifier = Modifier.fillMaxSize())
        }
        Counter({ reps }, ring.reps, { feedback }, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 28.dp))
        if (stuck) {
            SecondaryButton(
                "Solve $SUMS_TO_SOLVE sums instead",
                { onStuck(if (reps > 0) workout() else null) },
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(28.dp),
            )
        }
    }
}

private fun live(status: Status) = when (status) {
    Status.GOOD -> Nova.LiveGood
    Status.FIX -> Nova.LiveWarn
    Status.SEARCHING -> Nova.LiveIdle
}

/** Count, progress and the coach's cue; reads its state through lambdas so only this part recomposes. */
@Composable
private fun Counter(reps: () -> Int, target: Int, feedback: () -> Feedback, modifier: Modifier) {
    Column(modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val done = reps()
        Column(
            Modifier
                .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.large)
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                Text("$done", style = MaterialTheme.typography.displayLarge, color = Color.White, modifier = Modifier.alignByBaseline())
                Text(
                    " / $target",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Meter(done / target.toFloat(), Nova.LiveGood, Modifier.width(160.dp))
        }
        val f = feedback()
        if (f.status != Status.GOOD) {
            Text(
                f.cue,
                style = MaterialTheme.typography.titleSmall,
                color = Nova.OnLive,
                maxLines = 2,
                modifier = Modifier.background(live(f.status), CircleShape).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun FullBrightness() {
    val window = (LocalActivity.current ?: return).window
    DisposableEffect(window) {
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }
}

private val KEYS = listOf("123", "456", "789", " 0${SumChallenge.BACKSPACE}")

/** 76 dp circles for sleepy thumbs, smaller when a short (16:9) screen has less height to share. */
private val KEY_SIZE = Modifier.sizeIn(maxWidth = 76.dp, maxHeight = 76.dp).aspectRatio(1f)

/** Its own keypad: the soft keyboard can't be relied on over the lock screen. */
@Composable
private fun MathsChallenge(onKey: () -> Unit, onDone: () -> Unit) {
    var state by remember { mutableStateOf(SumChallenge(Sum.random())) }
    LaunchedEffect(state.done) { if (state.done) onDone() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Steps(state.solved)
        Text("${state.sum.a} + ${state.sum.b}", style = MaterialTheme.typography.displayMedium, maxLines = 1)
        Text(
            "= ${state.typed.ifEmpty { "?" }}",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) { if (state.wrong) Tag("Not quite, new sum", Nova.colors.bad) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in KEYS) {
                // The rows share the height left below the sum, so all four always fit (fixed 76 dp keys didn't on 16:9).
                Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (k in row) {
                        if (k == ' ') Spacer(KEY_SIZE) else Key(k) { state = state.press(k) { Sum.random() }; onKey() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Steps(solved: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Sum ${minOf(solved + 1, SUMS_TO_SOLVE)} of $SUMS_TO_SOLVE" },
    ) {
        repeat(SUMS_TO_SOLVE) { i ->
            Box(Modifier.size(10.dp).background(if (i < solved) colors.primary else colors.surfaceContainerHighest, CircleShape))
        }
    }
}

@Composable
private fun Key(k: Char, onClick: () -> Unit) {
    val back = k == SumChallenge.BACKSPACE
    Box(
        KEY_SIZE
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (back) Modifier.semantics { contentDescription = "Delete" } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (back) Glyph(R.drawable.ic_back, size = 28) else Text("$k", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun GoodMorning(m: Morning, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        delay(30_000) // don't keep the screen on forever if they walk off
        onClose()
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Emoji(R.drawable.emoji_sunrise, 112.dp)
        Text("Good morning", style = MaterialTheme.typography.headlineMedium)
        if (m.slept != null || m.points != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (m.slept != null) StatTile("Slept", hm(m.slept), Modifier.weight(1f), emoji = R.drawable.emoji_sleeping)
                if (m.points != null) StatTile("Points", "+${m.points}", Modifier.weight(1f), emoji = R.drawable.emoji_bolt)
            }
        }
        PrimaryButton("Done", onClose)
    }
}
