package app.fitnova.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitnova.R
import app.fitnova.domain.Badge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DailyGoalSheet(goal: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    NovaSheet(onDismiss) {
        Text("Daily goal", style = MaterialTheme.typography.titleLarge)
        Text("Points per day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DailyGoalTiles(goal, onPick)
    }
}

/** A badge up close: artwork, name, how to earn it, and whether it is earned. */
@Composable
internal fun BadgeSheet(b: Badge, onDismiss: () -> Unit) {
    val n = Nova.colors
    NovaSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeArtwork(b, 96.dp)
            Spacer(Modifier.height(16.dp))
            Text(b.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(b.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            if (b.earned) Tag("Earned", n.good, dot = true) else Tag("Locked", dot = true)
        }
    }
}

/** Open-source notices from assets/licenses.txt, read off the main thread and laid out a paragraph at a time. */
@Composable
internal fun LicencesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState<List<Pair<String, Boolean>>?>(null) {
        value = withContext(Dispatchers.IO) {
            licenceParagraphs(runCatching { context.assets.open("licenses.txt").bufferedReader().use { it.readText() } }.getOrDefault(""))
        }
    }
    NovaSheet(onDismiss, scrollable = false) {
        Text("Open-source licences", style = MaterialTheme.typography.titleLarge)
        val list = paragraphs
        if (list == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list.size) { i ->
                    val (text, heading) = list[i]
                    Text(
                        text,
                        style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                        color = if (heading) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (heading) Modifier.padding(top = 12.dp) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * Splits the notices into paragraphs (text, isHeading). Lines framed by "====" rules become a heading; prose
 * hard-wrapped at ~70 columns is re-flowed so it fills a phone instead of breaking every line.
 */
internal fun licenceParagraphs(text: String): List<Pair<String, Boolean>> {
    val out = mutableListOf<Pair<String, Boolean>>()
    val lines = mutableListOf<String>()
    var heading = false
    fun flush() {
        if (lines.isEmpty()) return
        out += buildString {
            lines.forEachIndexed { i, line ->
                // A long line followed by more text was wrapped by hand; list items keep their own line.
                val join = i > 0 && lines[i - 1].length >= 60 && !line.trimStart().startsWith("- ")
                if (i > 0) append(if (join) ' ' else '\n')
                append(if (join) line.trimStart() else line)
            }
        } to heading
        lines.clear()
    }
    for (raw in text.lines()) {
        val line = raw.trimEnd()
        when {
            line.isNotEmpty() && line.all { it == '=' } -> {
                flush()
                heading = !heading
            }
            line.isEmpty() -> flush()
            else -> lines += line
        }
    }
    flush()
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(minuteOfDay: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text("Set", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant) }
        },
        title = { Text("Reminder time", style = MaterialTheme.typography.titleLarge) },
        text = { TimePicker(state) },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surfaceContainerHigh,
        tonalElevation = 0.dp,
    )
}

/** Shown once after Create account. No reset promise (there is no reset flow); Copy confirms inline. */
@Composable
internal fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {}, // shown only once, so it needs an explicit Done
        title = { Text("Your recovery code", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Shown once. Keep it safe.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                SelectionContainer {
                    Text(
                        code,
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp, fontFeatureSettings = "tnum"),
                        color = colors.onSurface,
                        modifier = Modifier.fillMaxWidth().background(colors.surfaceVariant, MaterialTheme.shapes.medium).padding(16.dp),
                    )
                }
                if (copied) {
                    Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                        Glyph(R.drawable.ic_check_circle, tint = Nova.colors.good, size = 18)
                        Spacer(Modifier.width(6.dp))
                        Text("Copied", style = MaterialTheme.typography.labelLarge, color = Nova.colors.good)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done", style = MaterialTheme.typography.labelLarge) } },
        dismissButton = {
            TextButton(onClick = {
                copyText(context, "Fit Nova recovery code", code, sensitive = true)
                copied = true
            }) { Text("Copy", style = MaterialTheme.typography.labelLarge) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surfaceContainerHigh,
        tonalElevation = 0.dp,
    )
}
