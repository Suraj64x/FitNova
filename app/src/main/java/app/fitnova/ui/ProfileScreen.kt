package app.fitnova.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateUtils
import androidx.compose.foundation.background
import app.fitnova.domain.level
import app.fitnova.domain.Stats
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.BuildConfig
import app.fitnova.R
import app.fitnova.ThemeMode
import app.fitnova.data.SyncState

/** Full-screen page over the tabs; back closes it. */
@Composable
fun ProfileScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val pending = workouts.count { !it.synced }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val name = vm.username

    Column(
        Modifier
            .statusBarsPadding() // outside the scroll, so content never slides under the clock
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeScreen, modifier = Modifier.padding(end = 4.dp)) {
                Icon(painterResource(R.drawable.ic_chevron), contentDescription = "Back", modifier = Modifier.size(22.dp).rotate(180f))
            }
            Text("Profile", style = MaterialTheme.typography.headlineMedium)
        }
        ProfileCard(name ?: "Guest", stats)
        if (name == null) {
            Panel { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AuthForm(vm) } }
        } else {
            SyncCard(vm, pending)
        }

        SectionTitle("🏅 Badges", action = "${badges.count { it.earned }}/${badges.size}")
        BadgeGrid(badges)

        SectionTitle("⚙️ Settings")
        Panel(padding = PaddingValues(0.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("🎯 Daily goal", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                val goals = listOf(30, 50, 80, 120)
                Segmented(goals.map { "$it" }, goals.indexOf(vm.dailyGoal).coerceAtLeast(0), { vm.changeDailyGoal(goals[it]) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { vm.editingBody = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("🥗 Body & nutrition", style = MaterialTheme.typography.titleSmall)
                    val b = vm.body
                    Text(
                        if (b == null) "Tap to set up" else "${b.heightCm} cm · ${b.weightKg} kg · ${vm.targets?.kcal ?: 0} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Glyph(R.drawable.ic_chevron, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(16.dp)) {
                Text("🎨 Theme", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Segmented(listOf("System", "Light", "Dark"), ThemeMode.entries.indexOf(vm.theme), { vm.changeTheme(ThemeMode.entries[it]) })
            }
        }

        if (name != null) {
            Panel(onClick = { if (pending > 0) confirmSignOut = true else vm.signOut() }, padding = PaddingValues(16.dp)) {
                Text("🚪 Sign out", style = MaterialTheme.typography.titleSmall, color = Tone.bad)
            }
        }
        Text(
            "Fit Nova ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Sign out?") },
            text = { Text("$pending workout(s) not uploaded yet.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out", color = Tone.bad) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    vm.auth.recoveryCode?.let { code -> RecoveryCodeDialog(code, vm::dismissRecoveryCode) }
}

/** Avatar, level with XP bar, and lifetime stats. */
@Composable
private fun ProfileCard(name: String, stats: Stats) {
    val lv = level(stats.totalPoints)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Tone.accent),
                contentAlignment = Alignment.Center,
            ) { Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = Color.White) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                Text("⭐ Level ${lv.number} · ${lv.title}", style = MaterialTheme.typography.labelLarge, color = Tone.accent)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row {
            Text("${lv.into} / ${lv.span} XP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Lv ${lv.number + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Meter(lv.progress, Tone.accent, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("🏋️", "${stats.totalWorkouts}", "Workouts", Modifier.weight(1f))
            StatBox("⚡", "${stats.totalPoints}", "Points", Modifier.weight(1f))
            StatBox("🔥", "${stats.bestStreak}", "Best streak", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatBox(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Sync status at a glance with a one-tap sync. */
@Composable
private fun SyncCard(vm: AppViewModel, pending: Int) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    data class Look(val icon: String, val title: String, val sub: String, val tone: Color)
    val look = when (val s = sync) {
        SyncState.Running -> Look("🔄", "Syncing…", "Uploading and refreshing", Tone.accent)
        is SyncState.Failed -> if (s.offline) Look("📴", "Offline", if (pending > 0) "$pending waiting to upload" else "Will sync when online", Tone.warn)
        else Look("⚠️", "Sync issue", s.message, Tone.bad)
        is SyncState.Done -> if (pending > 0) Look("⏳", "$pending waiting", "Tap sync to upload", Tone.warn)
        else Look("☁️", "All synced", "Last synced ${DateUtils.getRelativeTimeSpanString(s.at)}", Tone.good)
        SyncState.Idle -> Look("☁️", "Not synced yet", if (pending > 0) "$pending waiting" else "Tap sync", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Panel(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(look.tone.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text(look.icon, style = MaterialTheme.typography.titleMedium) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(look.title, style = MaterialTheme.typography.titleSmall)
                Text(look.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Chip("Sync", selected = true, onClick = { vm.syncNow(manual = true) })
        }
    }
}

@Composable
private fun AuthForm(vm: AppViewModel) {
    var create by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val auth = vm.auth
    val minLength = if (create) 12 else 1
    val ready = username.isNotBlank() && password.length >= minLength

    Text(if (create) "✨ Create account" else "🔐 Sign in", style = MaterialTheme.typography.titleLarge)
    Text(
        "Old Fit Nova accounts work here.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Segmented(listOf("Sign in", "Create account"), if (create) 1 else 0, { create = it == 1 })
    OutlinedTextField(
        value = username,
        onValueChange = { username = it.take(32) },
        label = { Text("Username") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        supportingText = if (create) ({ Text("12+ characters") }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
    auth.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.bad) }
    PrimaryButton(
        if (create) "Create account" else "Sign in",
        onClick = { vm.signIn(username, password, create) },
        enabled = ready,
        loading = auth.busy,
    )
}

@Composable
private fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {}, // must be acknowledged: the code is shown only once
        title = { Text("Save your recovery code") },
        text = {
            Column {
                Text("You'll need it to reset your password. It's shown only once.")
                Spacer(Modifier.height(12.dp))
                Text(code, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("I saved it") } },
        dismissButton = {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Fit Nova recovery code", code))
            }) { Text("Copy") }
        },
    )
}

/** Earned badges in colour on a tinted disc; locked ones faded. */
@Composable
private fun BadgeGrid(badges: List<app.fitnova.domain.Badge>) {
    Panel(padding = PaddingValues(12.dp)) {
        badges.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { b ->
                    Column(Modifier.weight(1f).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (b.earned) Tone.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(b.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.alpha(if (b.earned) 1f else 0.25f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            b.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (b.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
