package app.fitnova.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.ThemeMode
import app.fitnova.data.SyncState

@Composable
fun ProfileScreen(vm: AppViewModel, padding: PaddingValues) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val name = vm.username
        if (name == null) AuthForm(vm) else Account(vm, name)

        val badges by vm.badges.collectAsStateWithLifecycle()
        SectionTitle("🏅 Badges  ${badges.count { it.earned }}/${badges.size}")
        BadgeGrid(badges)

        SectionTitle("🎯 Daily goal")
        val goals = listOf(30, 50, 80, 120)
        Segmented(goals.map { "$it pts" }, goals.indexOf(vm.dailyGoal).coerceAtLeast(0), { vm.changeDailyGoal(goals[it]) })

        SectionTitle("🥗 Body & nutrition")
        Panel(onClick = { vm.editingBody = true }) {
            val b = vm.body
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (b == null) "Not set" else "${b.heightCm} cm · ${b.weightKg} kg · ${b.goal.label}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        vm.targets?.let { "${it.kcal} kcal and ${it.protein} g protein a day" } ?: "Tap to set up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Glyph(R.drawable.ic_chevron, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18)
            }
        }

        SectionTitle("🎨 Appearance")
        Segmented(
            options = listOf("System", "Light", "Dark"),
            selected = ThemeMode.entries.indexOf(vm.theme),
            onSelect = { vm.changeTheme(ThemeMode.entries[it]) },
        )
    }

    vm.auth.recoveryCode?.let { code -> RecoveryCodeDialog(code, vm::dismissRecoveryCode) }
}

@Composable
private fun Account(vm: AppViewModel, name: String) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val pending = workouts.count { !it.synced }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text("Fit Nova member", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Workouts", "${stats.totalWorkouts}", Modifier.weight(1f))
        StatTile("Points", "${stats.totalPoints}", Modifier.weight(1f))
        StatTile("Streak", "${stats.streak}", Modifier.weight(1f), unit = "d")
    }

    SectionTitle("☁️ Sync")
    Panel {
        val status = when (val s = sync) {
            SyncState.Running -> "Syncing…"
            is SyncState.Done -> "Last synced ${DateUtils.getRelativeTimeSpanString(s.at)}"
            is SyncState.Failed -> s.message
            SyncState.Idle -> "Not synced yet"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (pending == 0) "✓ All backed up" else "⏳ $pending waiting",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Sync now", onClick = vm::syncNow)
    }

    Spacer(Modifier.height(4.dp))
    SecondaryButton("Sign out", onClick = { if (pending > 0) confirmSignOut = true else vm.signOut() }, color = Tone.bad)

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("$pending workout(s) not uploaded yet.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
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

    ExerciseIcon(app.fitnova.domain.Exercise.LUNGE, Modifier.size(110.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if (create) "Create account" else "Sign in", style = MaterialTheme.typography.headlineMedium)
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
