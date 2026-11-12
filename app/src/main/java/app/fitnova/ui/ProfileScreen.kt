package app.fitnova.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.BuildConfig
import app.fitnova.R
import app.fitnova.ThemeMode
import app.fitnova.data.SyncState
import app.fitnova.domain.Badge
import app.fitnova.domain.Stats
import app.fitnova.domain.level

private const val CREDITS_URL = "https://github.com/Suraj64x/FitNova/tree/main/docs"
private val AuthModes = listOf("Sign in", "Create account")
private val ThemeLabels = listOf("Auto", "Light", "Dark")

/** Full-screen page over the tabs; back closes it. */
@Composable
fun ProfileScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val pending = remember(workouts) { workouts.count { !it.synced } }
    val earned = remember(badges) { badges.count { it.earned } }
    val name = vm.username
    var goalSheet by rememberSaveable { mutableStateOf(false) }
    var licences by rememberSaveable { mutableStateOf(false) }
    var timePicker by rememberSaveable { mutableStateOf(false) }
    var badgeId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val gutter = socialGutter()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("Profile", onBack = vm::closeScreen, modifier = Modifier.padding(horizontal = 4.dp))
        Column(
            Modifier
                .weight(1f)
                .imePadding() // keeps the focused sign-in field above the keyboard
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = gutter, end = gutter, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileHero(name, stats)
            if (name == null) AuthCard(vm) else SyncCard(vm, pending)
            SectionTitle("Badges", emoji = R.drawable.emoji_sports_medal, action = "$earned/${badges.size}")
            BadgeGrid(badges) { badgeId = it }
            SectionTitle("Settings", emoji = R.drawable.emoji_gear)
            SettingsCard(vm, onGoal = { goalSheet = true }, onTime = { timePicker = true })
            SectionTitle("About", emoji = R.drawable.emoji_phone)
            AboutCard(onLicences = { licences = true })
            if (name != null) SecondaryButton("Sign out", { confirmSignOut = true }, color = Nova.colors.bad)
        }
    }

    if (goalSheet) {
        DailyGoalSheet(
            vm.dailyGoal,
            onPick = {
                vm.changeDailyGoal(it)
                goalSheet = false
            },
            onDismiss = { goalSheet = false },
        )
    }
    if (licences) LicencesSheet { licences = false }
    val reminder = vm.reminder
    if (timePicker && reminder != null) ReminderTimeDialog(reminder, onPick = vm::changeReminder, onDismiss = { timePicker = false })
    badgeId?.let { id -> badges.firstOrNull { it.id == id }?.let { BadgeSheet(it) { badgeId = null } } }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            text = when (pending) {
                0 -> "Your synced workouts stay in your account."
                1 -> "1 unsynced workout stays here and uploads to the next account you sign in to."
                else -> "$pending unsynced workouts stay here and upload to the next account you sign in to."
            },
            confirm = "Sign out",
            destructive = true,
            onConfirm = vm::signOut,
            onDismiss = { confirmSignOut = false },
        )
    }
    vm.auth.recoveryCode?.let { RecoveryCodeDialog(it, vm::dismissRecoveryCode) }
}

/** Avatar, name, level with its XP meter, and three lifetime numbers. */
@Composable
private fun ProfileHero(name: String?, stats: Stats) {
    val lv = remember(stats.totalPoints) { level(stats.totalPoints) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    HeroPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name ?: "Guest", style = type.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Emoji(R.drawable.emoji_glowing_star, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Level ${lv.number} · ${lv.title}", style = type.labelLarge, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.semantics(mergeDescendants = true) {}) {
            Text("${lv.into} / ${lv.span} XP", style = type.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Lv ${lv.number + 1}", style = type.labelMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Meter(lv.progress, Nova.colors.points, Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroStat(R.drawable.emoji_lifter, "${stats.totalWorkouts}", "Workouts", Modifier.weight(1f))
            HeroStat(R.drawable.emoji_bolt, "${stats.totalPoints}", "Points", Modifier.weight(1f))
            HeroStat(R.drawable.emoji_fire, "${stats.bestStreak}", "Best streak", Modifier.weight(1f))
        }
    }
}

/** Number-first stat, centred so a 3-up row fits 320 dp screens (a StatTile label row would cut off). The number shrinks to fit. */
@Composable
private fun HeroStat(@DrawableRes emoji: Int, value: String, label: String, modifier: Modifier) {
    val type = MaterialTheme.typography
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        EmojiTile(emoji, size = 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = type.headlineSmall,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = type.labelLarge.fontSize, maxFontSize = type.headlineSmall.fontSize, stepSize = 1.sp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private class ProfileSyncLook(@param:DrawableRes val icon: Int, val tint: Color, val title: String, val line: String?)

/** Sync state as an icon and words, when it last synced, and a Sync button that waits while one runs. */
@Composable
private fun SyncCard(vm: AppViewModel, pending: Int) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    val n = Nova.colors
    val colors = MaterialTheme.colorScheme
    val now = rememberMinuteClock()
    val waiting = if (pending > 0) "$pending waiting" else null
    val look = when (val s = sync) {
        SyncState.Running -> ProfileSyncLook(R.drawable.ic_sync_duo, colors.primary, "Syncing…", waiting)
        is SyncState.Failed ->
            if (s.offline) ProfileSyncLook(R.drawable.ic_cloud_off_duo, n.warn, "Offline", waiting ?: "Syncs when online")
            else ProfileSyncLook(R.drawable.ic_warning_duo, n.bad, "Sync issue", s.message)
        is SyncState.Done ->
            if (waiting != null) ProfileSyncLook(R.drawable.ic_cloud_up_duo, n.warn, waiting, "Synced ${updatedAgo(s.at, now)}")
            else ProfileSyncLook(R.drawable.ic_cloud_check_duo, n.good, "All synced", "Synced ${updatedAgo(s.at, now)}")
        SyncState.Idle -> ProfileSyncLook(R.drawable.ic_cloud_up_duo, colors.onSurfaceVariant, waiting ?: "Not synced yet", null)
    }
    val running = sync == SyncState.Running
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(look.icon, look.tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(look.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                look.line?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            SocialPill("Sync", { vm.syncNow(manual = true) }, icon = R.drawable.ic_sync, enabled = !running, loading = running)
        }
    }
}

/** Optional account: Sign in / Create account, username, password with an eye toggle; Done submits. */
@Composable
private fun AuthCard(vm: AppViewModel) {
    var create by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // never in the saved-state bundle
    var show by remember { mutableStateOf(false) }
    val hidden = remember { PasswordVisualTransformation() }
    val auth = vm.auth
    val colors = MaterialTheme.colorScheme
    val ready = username.isNotBlank() && password.length >= if (create) 12 else 1
    val submit = { if (ready && !auth.busy) vm.signIn(username, password, create) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Illustration(Illu.SIGN_IN, height = 72.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Sync and compete", style = MaterialTheme.typography.titleMedium)
                Text("Optional · no email needed", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Segmented(AuthModes, if (create) 1 else 0, {
            create = it == 1
            vm.clearAuthError()
        })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it.take(32)
                vm.clearAuthError()
            },
            label = { Text("Username") },
            leadingIcon = { Glyph(R.drawable.ic_user, tint = colors.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                vm.clearAuthError()
            },
            label = { Text("Password") },
            leadingIcon = { Glyph(R.drawable.ic_lock, tint = colors.onSurfaceVariant) },
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        painterResource(if (show) R.drawable.ic_eye_off else R.drawable.ic_eye),
                        contentDescription = if (show) "Hide password" else "Show password",
                        modifier = Modifier.size(22.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            },
            visualTransformation = if (show) VisualTransformation.None else hidden,
            singleLine = true,
            supportingText = if (create) ({ Text("12+ characters") }) else null,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        auth.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_warning, tint = Nova.colors.bad, size = 16)
                Spacer(Modifier.width(6.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = Nova.colors.bad)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (create) "Create account" else "Sign in", submit, enabled = ready, loading = auth.busy)
    }
}

/** Badges three to a row; tap one for how to earn it. */
@Composable
private fun BadgeGrid(badges: List<Badge>, onOpen: (String) -> Unit) {
    val rows = remember(badges) { badges.chunked(3) }
    Panel(padding = PaddingValues(8.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth()) {
                for (b in row) BadgeCell(b, { onOpen(b.id) }, Modifier.weight(1f))
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeCell(b: Badge, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "Details", role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (b.earned) "Earned" else "Locked, ${b.hint}" }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgeArtwork(b, 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            b.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (b.earned) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val BadgeEmoji = mapOf(
    "first" to R.drawable.emoji_party,
    "streak3" to R.drawable.emoji_fire,
    "streak7" to R.drawable.emoji_bolt,
    "streak30" to R.drawable.emoji_trophy,
    "reps100" to R.drawable.emoji_hundred,
    "reps1000" to R.drawable.emoji_rocket,
    "perfect" to R.drawable.emoji_target,
    "plank2" to R.drawable.emoji_biceps,
    "variety" to R.drawable.emoji_sparkles,
    "early" to R.drawable.emoji_sunrise,
    "owl" to R.drawable.emoji_moon,
    "points500" to R.drawable.emoji_glowing_star,
)

/**
 * Earned: Fluent artwork on a brand tint. Locked: the artwork as a flat silhouette at the 35 % ink level plus a lock.
 * The silhouette is an opaque tint, so locked badges need no alpha layer.
 */
@Composable
internal fun BadgeArtwork(b: Badge, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val art = BadgeEmoji[b.id] ?: R.drawable.emoji_star
    if (b.earned) {
        EmojiTile(art, tint = colors.primary, size = size)
        return
    }
    val ink = colors.onSurfaceVariant.copy(alpha = 0.35f).compositeOver(colors.surfaceVariant)
    val silhouette = remember(ink) { ColorFilter.tint(ink) }
    Box(Modifier.size(size)) {
        Box(Modifier.matchParentSize().background(colors.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(art), null, Modifier.size(size * 0.6f), colorFilter = silhouette)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.38f)
                .background(colors.surfaceContainer, CircleShape)
                .padding(2.dp)
                .background(colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(size * 0.2f), tint = colors.onSurfaceVariant) }
    }
}

/** Goal, body, theme, voice coach, reminder and sleep goal. */
@Composable
private fun SettingsCard(vm: AppViewModel, onGoal: () -> Unit, onTime: () -> Unit) {
    val n = Nova.colors
    val context = LocalContext.current
    var blocked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberNotificationAsk { ok ->
        blocked = !ok
        if (ok) vm.setReminderOn(true)
    }
    val reminder = vm.reminder
    Panel(padding = PaddingValues(vertical = 4.dp)) {
        NavRow(R.drawable.ic_target_duo, "Daily goal", "${vm.dailyGoal} pts", onGoal, tint = n.points)
        NavRow(
            R.drawable.ic_bowl_duo,
            "Body & nutrition",
            vm.targets?.let { "${it.kcal} kcal" } ?: "Set up",
            { vm.editingBody = true },
            tint = n.protein,
        )
        ThemeRow(vm.theme, vm::changeTheme)
        ToggleRow(R.drawable.ic_speaker_duo, "Voice coach", checked = vm.voice, onCheckedChange = vm::changeVoice, tint = n.minutes)
        ToggleRow(
            R.drawable.ic_bell_duo,
            "Workout reminder",
            subtitle = if (blocked && reminder == null) "Allow notifications first" else null,
            checked = reminder != null,
            onCheckedChange = { on -> if (on) ask() else vm.setReminderOn(false) },
            tint = n.carbs,
        )
        if (reminder != null) {
            NavRow(R.drawable.ic_clock_duo, "Reminder time", reminderTime(reminder, DateFormat.is24HourFormat(context)), onTime, tint = n.carbs)
        }
        SleepGoalRow(vm.sleepGoal, vm::changeSleepGoal)
    }
}

@Composable
private fun ThemeRow(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(R.drawable.ic_palette_duo, Nova.colors.water)
        Text("Theme", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Segmented(ThemeLabels, ThemeMode.entries.indexOf(mode), { onChange(ThemeMode.entries[it]) }, Modifier.width(176.dp))
    }
}

/** Sleep goal from 6 to 10 hours in 15-minute steps. */
@Composable
private fun SleepGoalRow(goal: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(R.drawable.ic_moon_duo, Nova.colors.sleep)
            Spacer(Modifier.width(12.dp))
            Text("Sleep goal", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Stepper(goal, onChange, step = { 15 }, range = 360..600, format = ::sleepLength)
    }
}

/** 480 -> "8h", 450 -> "7h 30m". */
internal fun sleepLength(minutes: Int) = if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"

@Composable
private fun AboutCard(onLicences: () -> Unit) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Panel(padding = PaddingValues(vertical = 4.dp)) {
        ListRow(
            "Version",
            leading = { IconTile(R.drawable.ic_info_duo, muted) },
            trailing = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge, color = muted) },
        )
        NavRow(R.drawable.ic_notebook_duo, "Open-source licences", onClick = onLicences, tint = muted)
        NavRow(R.drawable.ic_link_duo, "Photo credits", onClick = { openLink(context, CREDITS_URL) }, tint = muted)
    }
}
