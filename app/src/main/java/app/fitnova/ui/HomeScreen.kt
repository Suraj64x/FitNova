package app.fitnova.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.Tab
import app.fitnova.data.SyncState
import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode
import app.fitnova.domain.Photo
import app.fitnova.domain.Stats
import app.fitnova.domain.Workout
import app.fitnova.domain.level
import app.fitnova.domain.next
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DefaultQuick = listOf(Exercise.PUSHUP, Exercise.SQUAT, Exercise.PLANK, Exercise.JACK, Exercise.LUNGE, Exercise.CURL, Exercise.HIGH_KNEES)
private const val MINUTES_GOAL = 30
private const val WATER_GOAL = 8
private val QuickWidth = 144.dp
private val QuickHeight = 184.dp

/** Page gutter: 16 dp, 20 dp from 400 dp wide. Shared by Home and History. */
@Composable
@ReadOnlyComposable
internal fun shellGutter(): Dp = if (LocalConfiguration.current.screenWidthDp >= 400) 20.dp else 16.dp

@Composable
fun HomeScreen(vm: AppViewModel, padding: PaddingValues) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableIntStateOf(0) }
    val gutter = shellGutter()
    val quick = remember(workouts) { quickMoves(workouts) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", contentType = "header") { HomeHeader(vm, today) }
        // A returning user sees placeholders until the history is read, never a flash of the first-run screen.
        if (!vm.loaded) {
            item(key = "loading", contentType = "loading") { HomeSkeleton() }
            return@LazyColumn
        }
        val fresh = workouts.isEmpty()
        if (fresh) {
            item(key = "welcome", contentType = "welcome") { WelcomeCard { vm.tab = Tab.TRAIN } }
        } else {
            item(key = "today", contentType = "today") { TodayCard(vm, stats) }
            item(key = "tiles", contentType = "tiles") { ProgressTiles(stats, workouts, today) }
            item(key = "night", contentType = "tiles") { NightTiles(vm, today) }
        }
        item(key = "quick-title", contentType = "title") {
            SectionTitle("Quick start", R.drawable.emoji_rocket, action = "See all", onAction = { vm.tab = Tab.TRAIN })
        }
        item(key = "quick", contentType = "quick") { QuickRow(quick, vm.exercisePhotos, gutter) { vm.setup = it } }
        if (fresh) return@LazyColumn
        item(key = "activity", contentType = "activity") { HomeActivityCard(stats, today, vm.dailyGoal, range) { range = it } }
        if (stats.formTrend.any { it != null }) item(key = "form", contentType = "form") { HomeFormCard(stats.formTrend, today) }
        if (stats.mix.isNotEmpty()) item(key = "mix", contentType = "mix") { HomeMixCard(stats.mix) }
        item(key = "recent-title", contentType = "title") {
            SectionTitle("Recent", R.drawable.emoji_calendar, action = "See all", onAction = { vm.openHistory() })
        }
        item(key = "recent", contentType = "recent") { RecentCard(workouts) { vm.openHistory(it) } }
    }
}

// ---- Header ----

@Composable
private fun HomeHeader(vm: AppViewModel, today: LocalDate) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val pending = remember(workouts) { workouts.count { !it.synced } }
    val locale = LocalConfiguration.current.locales[0]
    // The locale's own order ("Sat 7 Nov" or "Sat, Nov 7").
    val date = remember(today, locale) { today.format(localizedPattern(locale, "EEEdMMM")) }
    val name = vm.username
    ScreenHeader(greeting(name), subtitle = date) {
        SyncChip(sync, pending, signedIn = name != null) { if (name == null) vm.openProfile() else vm.syncNow(manual = true) }
        ProfileButton(name, vm::openProfile)
    }
}

// Short on purpose: next to the "Local" chip and the avatar, "Good afternoon" would be cut on a 360 dp phone.
private fun greeting(name: String?) = if (name != null) "Hi, $name" else "Hi there"

private class SyncLook(@param:DrawableRes val icon: Int, val tone: Color, val text: String?, val description: String)

/** Compact sync status: one cloud icon per state (plus a count), labelled for TalkBack. Signed out it opens Profile. */
@Composable
private fun SyncChip(state: SyncState, pending: Int, signedIn: Boolean, onClick: () -> Unit) {
    val n = Nova.colors
    val colors = MaterialTheme.colorScheme
    val look = when {
        !signedIn -> SyncLook(R.drawable.ic_cloud_off, colors.onSurfaceVariant, "Local", "Saved on this phone. Sign in to sync")
        state is SyncState.Running -> SyncLook(R.drawable.ic_sync, colors.primary, null, "Syncing")
        state is SyncState.Failed && state.offline ->
            SyncLook(R.drawable.ic_cloud_off, n.warn, pending.takeIf { it > 0 }?.toString(), "Offline, $pending waiting. Retry")
        state is SyncState.Failed -> SyncLook(R.drawable.ic_warning, n.bad, null, "Sync failed. Retry")
        pending > 0 -> SyncLook(R.drawable.ic_cloud_up, n.warn, "$pending", "$pending to upload. Sync now")
        state is SyncState.Done -> SyncLook(R.drawable.ic_cloud_check, n.good, null, "Synced. Sync again")
        else -> SyncLook(R.drawable.ic_sync, colors.onSurfaceVariant, null, "Sync now") // nothing has run yet: don't claim "synced"
    }
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(look.tone.copy(alpha = 0.14f))
            .clickable(enabled = !signedIn || state !is SyncState.Running, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (look.text == null) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(look.icon), look.description, Modifier.size(20.dp), tint = look.tone)
        if (look.text != null) {
            Spacer(Modifier.width(6.dp))
            Text(look.text, style = MaterialTheme.typography.labelLarge, color = look.tone, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

// ---- Today ----

/** The hero: three open arcs (points, minutes, water) beside a row per metric. Stacks on very narrow phones. */
@Composable
private fun TodayCard(vm: AppViewModel, stats: Stats) {
    val c = Nova.colors
    val goal = vm.dailyGoal.coerceAtLeast(1)
    val water = vm.water
    val points = stats.todayPoints
    val minutes = stats.todayMinutes
    val arcs = remember(points, minutes, water, goal, c) {
        listOf(points.toFloat() / goal to c.points, minutes.toFloat() / MINUTES_GOAL to c.minutes, water.toFloat() / WATER_GOAL to c.water)
    }
    val describe = "Today $points of $goal points, $minutes of $MINUTES_GOAL minutes, $water of $WATER_GOAL glasses of water"
    HeroPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (points >= goal) Tag("Goal hit", c.good, dot = true) else Tag("${goal - points} to go", MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        val metrics = @Composable { modifier: Modifier ->
            Column(modifier) {
                Metric(R.drawable.ic_lightning_duo, c.points, points, goal, "Points")
                Metric(R.drawable.ic_timer_duo, c.minutes, minutes, MINUTES_GOAL, "Minutes")
                Metric(R.drawable.ic_drop_duo, c.water, water, WATER_GOAL, "Water glasses", onAdd = { vm.addWater(1) }, onRemove = { vm.addWater(-1) })
            }
        }
        if (LocalConfiguration.current.screenWidthDp >= 360) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TodayArcs(arcs, points, describe)
                Spacer(Modifier.width(16.dp))
                metrics(Modifier.weight(1f))
            }
        } else {
            // 320 dp phones: side by side would leave the rows about 80 dp.
            TodayArcs(arcs, points, describe, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            metrics(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TodayArcs(arcs: List<Pair<Float, Color>>, points: Int, describe: String, modifier: Modifier = Modifier) {
    Arcs(arcs, size = 136.dp, stroke = 10.dp, modifier = modifier, contentDescription = describe) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val type = MaterialTheme.typography
            // The inner arc leaves about 56 dp: big counts and large font sizes shrink instead of running over the arcs.
            Text(
                "$points",
                style = type.headlineSmall,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = type.labelLarge.fontSize, maxFontSize = type.headlineSmall.fontSize, stepSize = 1.sp),
                modifier = Modifier.widthIn(max = 56.dp),
            )
            Text("pts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One metric: duotone icon tile, value/goal and a meter. With [onAdd] the whole row is the 48 dp water button
 * (tap adds a glass, long-press removes one), so the target is large without crowding the row.
 */
@Composable
private fun Metric(
    @DrawableRes icon: Int,
    tint: Color,
    value: Int,
    goal: Int,
    what: String,
    onAdd: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val press = if (onAdd != null) {
        Modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClickLabel = "Add a glass of water", onLongClickLabel = "Remove a glass", role = Role.Button, onLongClick = onRemove, onClick = onAdd)
    } else {
        Modifier
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(press)
            .clearAndSetSemantics { contentDescription = "$what, $value of $goal" }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint, 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                buildAnnotatedString {
                    append("$value")
                    withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("/$goal") }
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (onAdd != null) {
                Box(Modifier.size(28.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(16.dp), tint = colors.onSurface)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Meter(value.toFloat() / goal.coerceAtLeast(1), tint, Modifier.fillMaxWidth())
    }
}

// ---- Bento tiles ----

@Composable
private fun ProgressTiles(stats: Stats, workouts: List<Workout>, today: LocalDate) {
    val doneToday = remember(workouts, today) { workouts.firstOrNull()?.let { localDay(it.startedAt) == today } == true }
    // Evenings only: a streak of 2+ that today hasn't extended yet.
    val atRisk = stats.streak >= 2 && !doneToday && LocalTime.now().hour >= 18
    val level = remember(stats.totalPoints) { level(stats.totalPoints) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(
            R.drawable.emoji_fire, MaterialTheme.colorScheme.tertiary, "Streak",
            numberUnits("${stats.streak}" to if (stats.streak == 1) " day" else " days"),
            Modifier.weight(1f),
        ) {
            if (atRisk) Tag("At risk", Nova.colors.warn, dot = true) else Note("Best ${stats.bestStreak}")
        }
        Tile(R.drawable.emoji_bolt, Nova.colors.points, "Level ${level.number}", numberUnits("${stats.totalPoints}" to " pts"), Modifier.weight(1f)) {
            Meter(level.progress, Nova.colors.points, Modifier.fillMaxWidth())
        }
    }
}

/** Last night's sleep and the next alarm; both open the Sleep tab. */
@Composable
private fun NightTiles(vm: AppViewModel, today: LocalDate) {
    val sleep by vm.sleepStats.collectAsStateWithLifecycle()
    val bedtime by vm.bedtime.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    // Re-read the clock on every resume, so a rung alarm makes way for the next one.
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LifecycleResumeEffect(Unit) {
        now = ZonedDateTime.now()
        onPauseOrDispose { }
    }
    val next = remember(alarms, now) { alarms.mapNotNull { it.next(now) }.minByOrNull { it.toInstant() } }
    val clock = clockFormat()
    val last = sleep.lastNight
    val bed = bedtime
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(
            R.drawable.emoji_sleeping, Nova.colors.sleep, "Sleep",
            if (last == null) numberUnits("–" to "") else numberUnits("${last.minutes / 60}" to "h ", "${last.minutes % 60}" to "m"),
            Modifier.weight(1f),
            onClick = { vm.tab = Tab.SLEEP },
        ) {
            Note(
                when {
                    bed != null -> "In bed · ${clock.short(bed)}"
                    last == null -> "Not logged"
                    else -> "${clock.short(last.bedAt)} → ${clock.short(last.wakeAt)}"
                },
            )
        }
        Tile(
            R.drawable.emoji_alarm, Nova.colors.sleep, "Alarm",
            if (next == null) numberUnits("–" to "") else clock.split(next.toLocalTime()),
            Modifier.weight(1f),
            onClick = { vm.tab = Tab.SLEEP },
        ) {
            Note(
                when (val day = next?.toLocalDate()) {
                    null -> "No alarm"
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                },
            )
        }
    }
}

/** Number-first tile: emoji and label, one big number (shrinks to fit narrow phones and large fonts), one footer line. */
@Composable
private fun Tile(
    @DrawableRes emoji: Int,
    tint: Color,
    label: String,
    value: AnnotatedString,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    footer: @Composable () -> Unit,
) {
    val type = MaterialTheme.typography
    Panel(if (onClick == null) modifier.semantics(mergeDescendants = true) {} else modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(emoji, tint, 36.dp)
            Spacer(Modifier.width(10.dp))
            Text(label, style = type.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            value,
            style = type.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = type.titleMedium.fontSize, maxFontSize = type.headlineLarge.fontSize, stepSize = 1.sp),
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) { footer() }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** "7h 20m": numbers in the tile style, each unit small and quiet right after its number. */
@Composable
private fun numberUnits(vararg parts: Pair<String, String>): AnnotatedString {
    val unit = SpanStyle(fontSize = 0.42.em, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    return buildAnnotatedString {
        for ((number, label) in parts) {
            append(number)
            if (label.isNotEmpty()) withStyle(unit) { append(label) }
        }
    }
}

/** Clock times in the phone's 12/24-hour setting. */
private class ClockFormat(private val h24: Boolean, locale: Locale) {
    private val time = DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm", locale)
    private val marker = DateTimeFormatter.ofPattern("a", locale)
    private val zone = ZoneId.systemDefault()

    /** Without AM/PM, for compact "23:10 → 06:30" lines. */
    fun short(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(zone).format(time)

    @Composable
    fun split(t: LocalTime): AnnotatedString = if (h24) numberUnits(t.format(time) to "") else numberUnits(t.format(time) to " " + t.format(marker))
}

@Composable
private fun clockFormat(): ClockFormat {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val h24 = DateFormat.is24HourFormat(context)
    return remember(h24, locale) { ClockFormat(h24, locale) }
}

// ---- Quick start ----

private class QuickMove(val exercise: Exercise, val amount: String)

/** Recent moves first, with what was done last time, then the beginner defaults: 8 at most. */
private fun quickMoves(workouts: List<Workout>): List<QuickMove> {
    val last = LinkedHashMap<Exercise, Workout>()
    for (w in workouts) { // newest first, so the first one seen per move is the latest
        if (last.size == 8) break
        last.getOrPut(w.exercise) { w }
    }
    val done = last.values.map { QuickMove(it.exercise, amountText(it)) }
    val starters = DefaultQuick.filter { it !in last }.map { QuickMove(it, "${it.defaultTarget} ${it.unit}") }
    return (done + starters).take(8)
}

@Composable
private fun QuickRow(moves: List<QuickMove>, photos: Map<String, Photo>, gutter: Dp, onPick: (Exercise) -> Unit) {
    val px = with(LocalDensity.current) { QuickWidth.roundToPx() } // the photo area is at most this wide and tall
    LazyRow(
        Modifier.bleed(gutter),
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(moves, key = { it.exercise.id }, contentType = { "quick" }) { move ->
            val photo = photos[move.exercise.id]
            val size = Modifier.size(QuickWidth, QuickHeight)
            if (photo != null) {
                PhotoCard(photo.path, move.exercise.title, move.amount, px, onClick = { onPick(move.exercise) }, modifier = size)
            } else {
                ArtCard(move.exercise, move.amount, size) { onPick(move.exercise) }
            }
        }
    }
}

/** Same frame as [PhotoCard], with the stick figure on the category tint: no jump when the photos arrive. */
@Composable
private fun ArtCard(exercise: Exercise, amount: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(modifier.clip(shape).background(colors.surfaceContainer).border(1.dp, colors.outlineVariant, shape).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).background(Nova.colors.of(exercise.category).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { ExerciseIcon(exercise, Modifier.size(88.dp)) }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(exercise.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(amount, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Lets a horizontal list run edge to edge through the page gutters while its first card still lines up. */
private fun Modifier.bleed(gutter: Dp) = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

// ---- First run, loading, recent ----

@Composable
private fun WelcomeCard(onStart: () -> Unit) {
    HeroPanel {
        EmptyState("Let's get moving", illu = Illu.NO_WORKOUTS)
        PrimaryButton("Start", onStart)
    }
}

/** Flat placeholder blocks shaped like the real cards; no shimmer. */
@Composable
private fun HomeSkeleton() {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val shape = MaterialTheme.shapes.large
    Column(Modifier.semantics { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(236.dp).background(block, MaterialTheme.shapes.extraLarge))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(128.dp).background(block, shape)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(QuickHeight).background(block, shape))
    }
}

@Composable
private fun RecentCard(workouts: List<Workout>, onOpen: (LocalDate) -> Unit) {
    val recent = remember(workouts) { workouts.take(5) }
    Panel(padding = PaddingValues(vertical = 4.dp)) {
        recent.forEach { w -> key(w.id) { WorkoutRow(w) { onOpen(localDay(w.startedAt)) } } }
    }
}

/** A logged workout: move, amount, form (or manual) and how long ago. [onClick] opens it in History. */
@Composable
fun WorkoutRow(w: Workout, onClick: (() -> Unit)? = null) {
    val now = System.currentTimeMillis()
    val ago = if (now - w.startedAt < DateUtils.MINUTE_IN_MILLIS) {
        "Now"
    } else {
        DateUtils.getRelativeTimeSpanString(w.startedAt, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }
    ListRow(
        w.exercise.title,
        subtitle = "${amountText(w)} · ${if (w.tracked) "${w.form}%" else "Manual"} · $ago",
        leading = { ExerciseTile(w.exercise, 40) },
        trailing = { Text("+${w.points}", style = MaterialTheme.typography.labelLarge) },
        onClick = onClick,
    )
}

// ---- Helpers ----

private fun amountText(w: Workout) = if (w.exercise.mode == Mode.HOLD) "${w.seconds} sec" else "${w.reps} reps"

private fun localDay(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * A formatter in the locale's own field order for [skeleton] ("EEEdMMM" → "Sat 7 Nov" or "Sat, Nov 7").
 * The pattern comes from ICU; should java.time ever reject one of its letters, the skeleton's own fields are used
 * with spaces between them ("EEE d MMM").
 */
internal fun localizedPattern(locale: Locale, skeleton: String): DateTimeFormatter =
    runCatching { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
        .getOrElse { DateTimeFormatter.ofPattern(Regex("(.)\\1*").findAll(skeleton).joinToString(" ") { it.value }, locale) }
