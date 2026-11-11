package app.fitnova.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitnova.AppViewModel
import app.fitnova.R
import kotlinx.coroutines.launch
import java.util.Locale

/** Minute of day a new reminder rings at: 19:00. */
internal const val DEFAULT_REMINDER = 19 * 60

private const val PAGES = 3

/** First launch: two intro pages, then the daily goal and an optional reminder. Skip keeps the defaults. */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    var goal by rememberSaveable { mutableIntStateOf(vm.dailyGoal) }
    var remind by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberNotificationAsk { ok ->
        remind = ok
        blocked = !ok
    }
    val page = pager.currentPage
    val last = page == PAGES - 1
    BackHandler(enabled = page > 0) { scope.launch { pager.animateScrollToPage(page - 1) } }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = socialGutter())) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Emoji(R.drawable.emoji_sparkles, 24.dp)
            Spacer(Modifier.size(8.dp))
            Text("Fit Nova", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = { vm.finishOnboarding(null) }) { Text("Skip", style = MaterialTheme.typography.labelLarge) }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { p ->
            when (p) {
                0 -> IntroPage(Illu.WELCOME, "Train smarter at home", "No gym, no gear")
                1 -> IntroPage(Illu.CAMERA_COACH, "Your camera counts reps and checks form", "Video stays on your phone")
                else -> GoalPage(goal, { goal = it }, remind, blocked, onRemind = { on -> if (on) ask() else remind = false })
            }
        }
        PageDots(page)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            if (last) "Start" else "Next",
            onClick = {
                if (last) {
                    if (remind) vm.changeReminder(DEFAULT_REMINDER)
                    vm.finishOnboarding(goal)
                } else {
                    scope.launch { pager.animateScrollToPage(page + 1) }
                }
            },
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IntroPage(illu: Illu, title: String, line: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Illustration(illu, height = 240.dp)
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GoalPage(goal: Int, onGoal: (Int) -> Unit, remind: Boolean, blocked: Boolean, onRemind: (Boolean) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmojiTile(R.drawable.emoji_target, size = 88.dp)
        Spacer(Modifier.height(20.dp))
        Text("Daily goal", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("Points per day", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        DailyGoalTiles(goal, onGoal)
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp)) {
            ToggleRow(
                R.drawable.ic_bell_duo,
                "Remind me daily",
                subtitle = if (blocked) "Allow notifications first" else reminderTime(DEFAULT_REMINDER, DateFormat.is24HourFormat(context)),
                checked = remind,
                onCheckedChange = onRemind,
                tint = Nova.colors.carbs,
            )
        }
    }
}

/** Nova dots: the current page is a wider pill. */
@Composable
private fun PageDots(page: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Page ${page + 1} of $PAGES" },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(PAGES) { i ->
            val on = i == page
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (on) 24.dp else 8.dp, height = 8.dp)
                    .background(if (on) colors.primary else colors.outlineVariant, CircleShape),
            )
        }
    }
}

// ---- Shared with Profile ----

private class GoalOption(val points: Int, val label: String, @param:DrawableRes val art: Int)

private val GoalOptions = listOf(
    GoalOption(30, "easy", R.drawable.emoji_seedling),
    GoalOption(50, "steady", R.drawable.emoji_runner),
    GoalOption(80, "strong", R.drawable.emoji_biceps),
    GoalOption(120, "beast", R.drawable.emoji_lifter),
)

/** The four daily points goals as picture tiles (radio group). */
@Composable
internal fun DailyGoalTiles(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in GoalOptions) {
            val on = o.points == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (on) colors.primary.copy(alpha = 0.12f) else colors.surfaceContainer)
                    .border(if (on) 2.dp else 1.dp, if (on) colors.primary else colors.outlineVariant, shape)
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(o.points) })
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmojiTile(o.art, size = 44.dp)
                Spacer(Modifier.height(8.dp))
                Text("${o.points}", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                Text(o.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** "19:00", or "7:00 PM" on 12-hour phones; ASCII digits in every locale. */
internal fun reminderTime(minuteOfDay: Int, is24: Boolean): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    return if (is24) {
        String.format(Locale.ROOT, "%02d:%02d", h, m)
    } else {
        String.format(Locale.ROOT, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
    }
}

/**
 * Asks for notifications (Android 13+) before a reminder goes on, and reports whether they are allowed.
 * After a refusal the next try opens the app's notification settings, since Android stops asking.
 */
@Composable
internal fun rememberNotificationAsk(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    var refused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refused = !ok
        result(ok)
    }
    return {
        val allowed = context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: true
        when {
            allowed -> result(true)
            Build.VERSION.SDK_INT >= 33 && !refused -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> {
                openNotificationSettings(context)
                result(false)
            }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}
