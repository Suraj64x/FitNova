package app.fitnova.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.alarm.readiness
import app.fitnova.domain.Alarm
import app.fitnova.domain.DayTotal
import app.fitnova.domain.MAX_SLEEP_MINUTES
import app.fitnova.domain.SleepEntry
import app.fitnova.domain.SleepStats
import app.fitnova.domain.WEEKDAYS
import app.fitnova.domain.hm
import app.fitnova.domain.repeatText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** Times in the user's 12/24-hour setting. */
internal class ClockFormat(private val is24: Boolean) {
    private val full = DateTimeFormatter.ofPattern(if (is24) "H:mm" else "h:mm a")
    private val digits = DateTimeFormatter.ofPattern(if (is24) "H:mm" else "h:mm")
    private val half = DateTimeFormatter.ofPattern("a")

    fun format(t: LocalTime): String = t.format(full)
    fun format(ms: Long): String = format(local(ms).toLocalTime())

    /** The time without AM/PM, and AM/PM alone (null in 24-hour), for a big number with a small suffix. */
    fun digits(t: LocalTime): String = t.format(digits)
    fun half(t: LocalTime): String? = if (is24) null else t.format(half)
}

@Composable
internal fun rememberClockFormat(): ClockFormat {
    val context = LocalContext.current
    return remember { ClockFormat(DateFormat.is24HourFormat(context)) }
}

private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

private const val PAGE = 10
private val LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val NIGHT_DAY = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
fun SleepScreen(vm: AppViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val bedtime by vm.bedtime.collectAsStateWithLifecycle()
    val stats by vm.sleepStats.collectAsStateWithLifecycle()
    val nights by vm.sleep.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val clock = rememberClockFormat()

    // Permissions can change in Settings at any time: check again on every return to the app.
    var ready by remember { mutableStateOf(readiness(context)) }
    LifecycleResumeEffect(Unit) {
        ready = readiness(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(ready.exact) {
        // Exact alarms were just allowed: arm what couldn't be armed (the system's own broadcast can lag).
        if (ready.exact && vm.alarms.value.any { it.enabled && it.nextAt == null }) vm.rearmAlarms()
    }

    var alarmSheet by remember { mutableStateOf<Alarm?>(null) }
    var nightSheet by remember { mutableStateOf<NightDraft?>(null) }
    var goalSheet by remember { mutableStateOf(false) }
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }

    // Asked in context, one at a time and each at most once per visit: right after an alarm is saved or switched on.
    // Notifications, then the camera, then the "Alarms & reminders" page; the setup card covers the rest.
    var askStep by remember { mutableIntStateOf(0) }
    var asked by rememberSaveable { mutableIntStateOf(0) } // bits: 1 notifications, 2 camera, 4 exact alarms
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askStep++ }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askStep++ }
    LaunchedEffect(askStep) {
        if (askStep == 0) return@LaunchedEffect
        ready = readiness(context)
        val notify = Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            notify && (asked and 1) == 0 -> askNotify.launch(Manifest.permission.POST_NOTIFICATIONS).also { asked = asked or 1 }
            !ready.camera && (asked and 2) == 0 -> askCamera.launch(Manifest.permission.CAMERA).also { asked = asked or 2 }
            !ready.exact && (asked and 4) == 0 -> openExactAlarmSettings(context).also { asked = asked or 4 }
        }
    }
    fun askOnce() = askStep++
    fun newAlarm() = Alarm(UUID.randomUUID().toString(), 7, 0, days = WEEKDAYS)

    val last = stats.lastNight ?: nights.firstOrNull()
    val visibleNights = remember(nights, shown) { nights.take(shown) }
    val gutter = if (LocalConfiguration.current.screenWidthDp >= 400) 20.dp else 16.dp
    LazyColumn(
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { ScreenHeader("Sleep", trailing = { ProfileButton(vm.username, vm::openProfile) }) }
        item(key = "hero") {
            SleepHero(
                bedtime, last, isToday = stats.lastNight != null, vm.sleepGoal, clock,
                onBed = vm::goToBed,
                onAwake = vm::wakeUp,
                onCancel = vm::cancelBed,
                onFix = { bedtime?.let { nightSheet = NightDraft.forgotten(it, vm.sleepGoal) } },
                onAdd = { nightSheet = NightDraft.new(today) },
            )
        }
        if (nights.isNotEmpty()) {
            item(key = "week") { WeekCard(stats.week, today, vm.sleepGoal, onGoal = { goalSheet = true }) }
            item(key = "tiles") { SleepTiles(stats, clock) }
        }

        item(key = "alarms") { SectionTitle("Alarms", emoji = R.drawable.emoji_alarm, action = "Add", onAction = { alarmSheet = newAlarm() }) }
        if (alarms.any { it.enabled } && !ready.all) {
            item(key = "setup") { AlarmSetup(ready, onChange = { ready = readiness(context) }) }
        }
        if (alarms.isEmpty()) {
            item(key = "no-alarms") {
                Panel(Modifier.fillMaxWidth()) {
                    EmptyState("Add a wake-up challenge", illu = Illu.ALARM_MORNING, action = "Add alarm", onAction = { alarmSheet = newAlarm() })
                }
            }
        }
        items(alarms, key = { "a:${it.id}" }, contentType = { "alarm" }) { a ->
            AlarmCard(
                a, clock,
                onClick = { alarmSheet = a },
                onToggle = { on ->
                    vm.toggleAlarm(a, on)
                    if (on) askOnce()
                },
            )
        }

        if (nights.isNotEmpty()) {
            item(key = "nights") {
                SectionTitle("Recent nights", emoji = R.drawable.emoji_zzz, action = "Add", onAction = { nightSheet = NightDraft.new(today) })
            }
            items(visibleNights, key = { "n:${it.id}" }, contentType = { "night" }) { n ->
                NightRow(n, clock, onClick = { nightSheet = NightDraft.of(n) })
            }
            if (nights.size > shown) {
                item(key = "more") {
                    TextButton({ shown += PAGE }, Modifier.fillMaxWidth()) { Text("Show more", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }

    alarmSheet?.let { a ->
        key(a.id) {
            AlarmSheet(
                a,
                isNew = alarms.none { it.id == a.id },
                onSave = {
                    vm.saveAlarm(it)
                    alarmSheet = null
                    askOnce()
                },
                onTest = vm::testRing,
                onDelete = {
                    vm.deleteAlarm(a)
                    alarmSheet = null
                },
                onDismiss = { alarmSheet = null },
            )
        }
    }
    nightSheet?.let { d ->
        key(d) {
            NightSheet(
                d,
                nights,
                onSave = { e ->
                    vm.saveSleep(e)
                    if (d.closesBedtime) vm.cancelBed()
                    nightSheet = null
                },
                onDelete = d.id?.let { id ->
                    {
                        vm.deleteSleep(id)
                        nightSheet = null
                    }
                },
                onDismiss = { nightSheet = null },
            )
        }
    }
    if (goalSheet) GoalSheet(vm.sleepGoal, vm::changeSleepGoal, onDismiss = { goalSheet = false })
}

/** Ticks on each new minute; Compose pauses it while the app is in the background. */
@Composable
private fun rememberMinuteTick(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(60_000 - System.currentTimeMillis() % 60_000)
        value = System.currentTimeMillis()
    }
}

private fun goalText(minutes: Int) = if (minutes % 60 == 0) "${minutes / 60}h" else hm(minutes)

/** The tab's one big card: asleep now, last night, or a first-run invitation; it holds the primary action. */
@Composable
private fun SleepHero(
    bedtime: Long?,
    last: SleepEntry?,
    isToday: Boolean,
    goal: Int,
    clock: ClockFormat,
    onBed: () -> Unit,
    onAwake: () -> Unit,
    onCancel: () -> Unit,
    onFix: () -> Unit,
    onAdd: () -> Unit,
) {
    HeroPanel(Modifier.fillMaxWidth()) {
        when {
            bedtime != null -> Asleep(bedtime, goal, clock, onAwake, onCancel, onFix)
            last != null -> LastSleep(last, isToday, goal, clock, onBed)
            else -> {
                Illustration(Illu.SLEEP, Modifier.align(Alignment.CenterHorizontally), height = 140.dp)
                Text(
                    "Track your sleep",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                )
                PrimaryButton("Going to bed", onBed)
                TextButton(onAdd, Modifier.align(Alignment.CenterHorizontally)) { Text("Add a past night", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun HeroTitle(@DrawableRes emoji: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Emoji(emoji, 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArcCentre(value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.Asleep(bedtime: Long, goal: Int, clock: ClockFormat, onAwake: () -> Unit, onCancel: () -> Unit, onFix: () -> Unit) {
    val now by rememberMinuteTick()
    val minutes = ((now - bedtime) / 60_000).toInt().coerceAtLeast(0)
    val stale = minutes > MAX_SLEEP_MINUTES
    HeroTitle(R.drawable.emoji_sleeping, "Sleeping since ${clock.format(bedtime)}")
    Arc(
        minutes / goal.toFloat(), Nova.colors.sleep, size = 176.dp, stroke = 14.dp,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
        contentDescription = "Asleep for ${hm(minutes)}",
    ) { ArcCentre(if (stale) "16h+" else hm(minutes), "of ${goalText(goal)}") }
    if (stale) {
        Text(
            "Forgot to log waking up?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        PrimaryButton("Log it", onFix)
    } else {
        PrimaryButton("I'm awake", onAwake)
    }
    TextButton(onCancel, Modifier.align(Alignment.CenterHorizontally)) {
        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.LastSleep(e: SleepEntry, isToday: Boolean, goal: Int, clock: ClockFormat, onBed: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val short = goal - e.minutes
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { HeroTitle(R.drawable.emoji_moon, if (isToday) "Last night" else local(e.wakeAt).format(NIGHT_DAY)) }
        if (short <= 0) Tag("Goal met", Nova.colors.good, dot = true) else Tag("${hm(short)} short", Nova.colors.warn, dot = true)
    }
    Arc(
        e.minutes / goal.toFloat(), Nova.colors.sleep, size = 176.dp, stroke = 14.dp,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
        contentDescription = "Slept ${hm(e.minutes)} of ${goalText(goal)}",
    ) { ArcCentre(hm(e.minutes), "of ${goalText(goal)}") }
    Row(
        Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph(R.drawable.ic_moon, tint = colors.onSurfaceVariant, contentDescription = "Bed")
        Text(clock.format(e.bedAt), style = MaterialTheme.typography.labelLarge)
        Text("→", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        Glyph(R.drawable.ic_sun, tint = colors.onSurfaceVariant, contentDescription = "Woke")
        Text(clock.format(e.wakeAt), style = MaterialTheme.typography.labelLarge)
    }
    PrimaryButton("Going to bed", onBed)
}

@Composable
private fun WeekCard(week: List<Pair<LocalDate, Int>>, today: LocalDate, goal: Int, onGoal: () -> Unit) {
    val days = remember(week) { week.map { DayTotal(it.first, it.second) } }
    val description = remember(week) {
        "Last 7 days: " + week.joinToString { (d, m) -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + (if (m > 0) hm(m) else "no log") }
    }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("7 nights", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onGoal, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Glyph(R.drawable.ic_target, tint = MaterialTheme.colorScheme.primary, size = 18)
                Spacer(Modifier.width(6.dp))
                Text("Goal ${goalText(goal)}", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        WeekBars(days, today, goal = goal, color = Nova.colors.sleep, format = ::hours, contentDescription = description)
    }
}

/** Bar labels: "7.5" for 7 h 30 m, "8" for a whole 8 hours. */
private fun hours(minutes: Int) = if (minutes % 60 == 0) "${minutes / 60}" else "%.1f".format(minutes / 60f)

@Composable
private fun SleepTiles(stats: SleepStats, clock: ClockFormat) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Average", stats.avgMinutes?.let(::hm) ?: "–", Modifier.weight(1f), emoji = R.drawable.emoji_moon)
            StatTile(
                "Bedtime",
                stats.avgBedMinuteOfDay?.let { clock.format(LocalTime.of(it / 60, it % 60)) } ?: "–",
                Modifier.weight(1f),
                emoji = R.drawable.emoji_bed,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Spread of bedtimes: lower is steadier.
            StatTile("Consistency", stats.consistency?.let { "±$it" } ?: "–", Modifier.weight(1f), unit = stats.consistency?.let { "min" }, emoji = R.drawable.emoji_target)
            StatTile("Sleep debt", hm(stats.debtMinutes), Modifier.weight(1f), emoji = R.drawable.emoji_hourglass)
        }
    }
}

@Composable
private fun AlarmCard(a: Alarm, clock: ClockFormat, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val time = LocalTime.of(a.hour, a.minute)
    Panel(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        clock.digits(time),
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (a.enabled) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                    clock.half(time)?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.alignByBaseline().padding(start = 4.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseTile(a.exercise, 28)
                    Text(
                        listOf("${a.reps} ${a.exercise.title.lowercase()}", a.label).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(checked = a.enabled, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = "Alarm ${clock.format(time)}" })
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayLetters(a.days, Modifier.weight(1f))
            // Enabled but not armed: exact alarms aren't allowed yet (the setup card above says how).
            if (a.enabled && a.nextAt == null) Tag("Can't ring", Nova.colors.warn, dot = true)
        }
    }
}

@Composable
private fun DayLetters(days: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.clearAndSetSemantics { contentDescription = repeatText(days) },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (days == 0) {
            Tag("Once")
        } else {
            LETTERS.forEachIndexed { i, letter ->
                val on = (days and (1 shl i)) != 0
                Box(Modifier.size(24.dp).background(if (on) colors.inverseSurface else colors.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Text(letter, style = MaterialTheme.typography.labelSmall, color = if (on) colors.inverseOnSurface else colors.onSurfaceVariant)
                }
            }
        }
    }
}

@DrawableRes
private fun sourceArt(source: String) = when (source) {
    "alarm" -> R.drawable.emoji_alarm
    "manual" -> R.drawable.emoji_bed
    else -> R.drawable.emoji_sleeping
}

@Composable
private fun NightRow(n: SleepEntry, clock: ClockFormat, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(0.dp), shape = MaterialTheme.shapes.medium) {
        ListRow(
            local(n.wakeAt).format(NIGHT_DAY),
            subtitle = "${clock.format(n.bedAt)} → ${clock.format(n.wakeAt)}",
            leading = { EmojiTile(sourceArt(n.source), Nova.colors.sleep, size = 40.dp) },
            trailing = { Text(hm(n.minutes), style = MaterialTheme.typography.labelLarge) },
        )
    }
}
