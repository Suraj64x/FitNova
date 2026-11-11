package app.fitnova.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitnova.R
import app.fitnova.alarm.CH_ALARM
import app.fitnova.alarm.Readiness
import app.fitnova.domain.ALARM_EXERCISES
import app.fitnova.domain.Alarm
import app.fitnova.domain.Exercise
import app.fitnova.domain.SleepEntry
import app.fitnova.domain.alarmReps
import app.fitnova.domain.hm
import app.fitnova.domain.manualEntry
import app.fitnova.domain.sleepProblem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

// Sheets, dialogs and the setup checklist of the Sleep tab.

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_FULL = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** Fluent artwork per wake-up challenge. */
internal val Exercise.alarmArt: Int
    @DrawableRes get() = when (this) {
        Exercise.JACK -> R.drawable.emoji_cartwheel
        Exercise.HIGH_KNEES -> R.drawable.emoji_runner
        Exercise.LUNGE -> R.drawable.emoji_shoe
        else -> R.drawable.emoji_leg
    }

/** Below this height the clock dial doesn't fit next to the rest of a sheet, so times are typed instead. */
@Composable
private fun compactHeight() = LocalConfiguration.current.screenHeightDp < 640

/**
 * M3 TimePicker (TimeInput on short screens) that fits this app's type scale. Use it, or [TimeDialog], for any time
 * picking (e.g. the workout reminder): a bare TimePicker clips its digits with our larger display styles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeField(state: TimePickerState, modifier: Modifier = Modifier) {
    // The picker sizes its digit boxes for M3's 57/45 sp display styles; ours are 96/64 sp, so scale them back here.
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val pickerColors = TimePickerDefaults.colors(
        periodSelectorSelectedContainerColor = colors.primaryContainer,
        periodSelectorSelectedContentColor = colors.onPrimaryContainer,
        periodSelectorBorderColor = colors.outlineVariant,
    )
    MaterialTheme(
        typography = type.copy(
            displayLarge = type.displayLarge.copy(fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = 0.sp),
            displayMedium = type.displayMedium.copy(fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
        ),
    ) {
        if (compactHeight()) TimeInput(state, modifier, colors = pickerColors) else TimePicker(state, modifier, colors = pickerColors)
    }
}

@Composable
private fun SheetLabel(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Add or edit a wake-up alarm: time, repeat days, challenge and reps, label, a test ring and delete. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmSheet(
    alarm: Alarm,
    isNew: Boolean,
    onSave: (Alarm) -> Unit,
    onTest: (Alarm) -> Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val time = rememberTimePickerState(alarm.hour, alarm.minute, DateFormat.is24HourFormat(context))
    var days by remember { mutableIntStateOf(alarm.days) }
    var exercise by remember { mutableStateOf(alarm.exercise) }
    var reps by remember { mutableIntStateOf(alarm.reps) }
    var label by remember { mutableStateOf(alarm.label) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    fun current() = alarm.copy(hour = time.hour, minute = time.minute, days = days, exercise = exercise, reps = reps, label = label.trim(), enabled = true)

    NovaSheet(onDismiss, footer = { PrimaryButton("Save", { onSave(current()) }) }, message = message) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isNew) "New alarm" else "Edit alarm", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!isNew) IconCircleButton(R.drawable.ic_trash, "Delete alarm", { confirming = true }, container = Color.Transparent, tint = Nova.colors.bad)
        }
        TimeField(time, Modifier.align(Alignment.CenterHorizontally))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SheetLabel(R.drawable.ic_repeat, "Repeat") }
            Chip("Once", days == 0, { days = 0 })
        }
        DayToggles(days) { days = it }

        SheetLabel(R.drawable.ic_sneaker, "Challenge")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in ALARM_EXERCISES) {
                Chip(e.title, exercise == e, { exercise = e; reps = alarmReps(e) }, emoji = e.alarmArt)
            }
        }
        Stepper(reps, { reps = it }, step = { 5 }, range = 5..50, unit = "reps")
        OutlinedTextField(
            label,
            { label = it.take(24) },
            Modifier.fillMaxWidth(),
            placeholder = { Text("Label") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        SecondaryButton("Test ring", {
            message = if (onTest(current())) {
                "⏰ Ringing in 5 s"
            } else {
                openExactAlarmSettings(context)
                "Allow Alarms & reminders, then test again"
            }
        })
    }
    if (confirming) ConfirmDialog("Delete alarm?", confirm = "Delete", destructive = true, onConfirm = onDelete, onDismiss = { confirming = false })
}

/** Seven round toggles, Monday first; none selected means the alarm rings once. */
@Composable
private fun DayToggles(days: Int, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        DAY_LETTERS.forEachIndexed { i, letter ->
            val on = (days and (1 shl i)) != 0
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .toggleable(
                        value = on,
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = 22.dp),
                        role = Role.Checkbox,
                        onValueChange = { onChange(days xor (1 shl i)) },
                    )
                    .semantics { contentDescription = DAY_FULL[i] },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp).background(if (on) colors.inverseSurface else colors.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) colors.inverseOnSurface else colors.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/** A night being added or edited, as the clock times the sheet shows. */
internal data class NightDraft(
    val id: String?,
    val source: String,
    val wakeDate: LocalDate,
    val bed: LocalTime,
    val wake: LocalTime,
    /** Logging a night left open too long: saving also closes it. */
    val closesBedtime: Boolean = false,
) {
    companion object {
        fun new(today: LocalDate) = NightDraft(null, "manual", today, LocalTime.of(23, 0), LocalTime.of(7, 0))

        fun of(e: SleepEntry): NightDraft {
            val wake = local(e.wakeAt)
            return NightDraft(e.id, e.source, wake.toLocalDate(), local(e.bedAt).toLocalTime().withSecond(0).withNano(0), wake.toLocalTime().withSecond(0).withNano(0))
        }

        /** "Going to bed" was never followed by "I'm awake": suggest the goal's length. */
        fun forgotten(bedtime: Long, goalMinutes: Int): NightDraft {
            val bed = local(bedtime)
            val wake = bed.plusMinutes(goalMinutes.toLong())
            return NightDraft(null, "tap", wake.toLocalDate(), bed.toLocalTime().withSecond(0).withNano(0), wake.toLocalTime().withSecond(0).withNano(0), closesBedtime = true)
        }

        private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
}

private val CHIP_DAY = DateTimeFormatter.ofPattern("EEE d")

/** Add or edit a night with two time pickers; checks length, the future and overlaps before it can be saved. */
@Composable
internal fun NightSheet(draft: NightDraft, others: List<SleepEntry>, onSave: (SleepEntry) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val clock = rememberClockFormat()
    val today = remember { LocalDate.now() }
    var wakeDate by remember { mutableStateOf(draft.wakeDate) }
    var bed by remember { mutableStateOf(draft.bed) }
    var wake by remember { mutableStateOf(draft.wake) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = bed, false = wake
    var confirming by remember { mutableStateOf(false) }
    val dates = remember(draft) { (listOf(draft.wakeDate) + (0L..6L).map { today.minusDays(it) }).distinct().sortedDescending() }
    val (bedAt, wakeAt) = manualEntry(wakeDate, bed, wake)
    val problem = sleepProblem(bedAt, wakeAt, System.currentTimeMillis(), others, draft.id)

    NovaSheet(
        onDismiss,
        footer = {
            PrimaryButton("Save", { onSave(SleepEntry(draft.id ?: UUID.randomUUID().toString(), bedAt, wakeAt, draft.source)) }, enabled = problem == null)
        },
        message = problem,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (draft.id == null) "Add sleep" else "Edit sleep", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onDelete != null) IconCircleButton(R.drawable.ic_trash, "Delete night", { confirming = true }, container = Color.Transparent, tint = Nova.colors.bad)
        }
        SheetLabel(R.drawable.ic_calendar, "Woke up")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in dates) {
                val text = when (d) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> d.format(CHIP_DAY)
                }
                Chip(text, wakeDate == d, { wakeDate = d })
            }
        }
        TimeRow(R.drawable.ic_moon_duo, "Bed", clock.format(bed)) { picking = true }
        TimeRow(R.drawable.ic_sun_duo, "Wake", clock.format(wake)) { picking = false }
        Text(
            hm(((wakeAt - bedAt) / 60_000).toInt().coerceAtLeast(0)),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
    picking?.let { forBed ->
        TimeDialog(if (forBed) bed else wake, onPick = { if (forBed) bed = it else wake = it }, onDismiss = { picking = null })
    }
    if (confirming && onDelete != null) {
        ConfirmDialog("Delete this night?", confirm = "Delete", destructive = true, onConfirm = onDelete, onDismiss = { confirming = false })
    }
}

@Composable
private fun TimeRow(@DrawableRes icon: Int, title: String, value: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(0.dp), shape = MaterialTheme.shapes.medium) {
        ListRow(title, leading = { IconTile(icon, Nova.colors.sleep) }, trailing = { Text(value, style = MaterialTheme.typography.headlineSmall) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant) }
        },
        text = { TimeField(state) },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surfaceContainerHigh,
        tonalElevation = 0.dp,
    )
}

/** Sleep goal, 6 to 10 hours in quarter hours. */
@Composable
internal fun GoalSheet(goal: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    NovaSheet(onDismiss, footer = { PrimaryButton("Done", onDismiss) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiTile(R.drawable.emoji_target, Nova.colors.sleep)
            Text("Sleep goal", style = MaterialTheme.typography.titleLarge)
        }
        Stepper(goal, onChange, step = { 15 }, range = 6 * 60..10 * 60, format = ::hm)
    }
}

private fun granted(c: Context, permission: String) = c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** A Settings page, or the app's info page when this phone doesn't have that one. */
internal fun openSettings(c: Context, intent: Intent) {
    runCatching { c.startActivity(intent) }.onFailure {
        runCatching { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))) }
    }
}

/** "Alarms & reminders" for this app (Android 12+, where exact alarms became user-granted). */
internal fun openExactAlarmSettings(c: Context) {
    if (Build.VERSION.SDK_INT >= 31) openSettings(c, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}")))
}

/**
 * What still stops alarms from ringing properly, each with its own fix. Rechecked on every resume by the caller
 * ([onChange] asks for a recheck right away). Exact alarms and full-screen access are Settings pages; notifications
 * and camera are runtime permissions, with Settings as the way back after "Don't ask again".
 */
@Composable
internal fun AlarmSetup(ready: Readiness, onChange: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val pkg = remember { Uri.parse("package:${context.packageName}") }
    fun open(intent: Intent) = openSettings(context, intent)
    fun appNotifications() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok && activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false) open(appNotifications())
        onChange()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        onChange()
    }

    Panel(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(R.drawable.ic_warning_duo, Nova.colors.warn)
            Text("Finish alarm setup", style = MaterialTheme.typography.titleMedium)
        }
        if (!ready.exact) Fix(R.drawable.ic_alarm_duo, "Alarms & reminders") { openExactAlarmSettings(context) }
        if (!ready.notifications) {
            Fix(R.drawable.ic_bell_duo, "Notifications") {
                when {
                    Build.VERSION.SDK_INT >= 33 && !granted(context, Manifest.permission.POST_NOTIFICATIONS) -> notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() -> open(appNotifications())
                    // Notifications are on but the alarm channel was turned down: no heads-up, no full screen.
                    else -> open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, CH_ALARM))
                }
            }
        }
        if (!ready.fullScreen && Build.VERSION.SDK_INT >= 34) {
            Fix(R.drawable.ic_bell_ring_duo, "Full-screen alarm") { open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)) }
        }
        if (!ready.volume) {
            Fix(R.drawable.ic_speaker_duo, "Alarm volume") {
                val audio = context.getSystemService(AudioManager::class.java)
                val level = (audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 0.7f).roundToInt().coerceAtLeast(1)
                runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, level, AudioManager.FLAG_SHOW_UI) }
                onChange()
            }
        }
        if (!ready.camera) Fix(R.drawable.ic_camera_duo, "Camera to count reps") { camera.launch(Manifest.permission.CAMERA) }
    }
}

@Composable
private fun Fix(@DrawableRes icon: Int, title: String, onFix: () -> Unit) {
    ListRow(
        title,
        leading = { IconTile(icon, MaterialTheme.colorScheme.primary) },
        trailing = { Text("Allow", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
        onClick = onFix,
    )
}
