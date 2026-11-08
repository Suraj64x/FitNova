package app.fitnova.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fitnova.AppViewModel
import app.fitnova.Graph
import app.fitnova.Period
import app.fitnova.R
import app.fitnova.Tab
import app.fitnova.data.Rank

private val Medals = listOf(Color(0xFFD4A72C), Color(0xFF9AA3AD), Color(0xFFB87333))

@Composable
fun RanksScreen(vm: AppViewModel, padding: PaddingValues) {
    val board = vm.board
    val signedIn = vm.username != null
    LaunchedEffect(signedIn) { if (signedIn) vm.loadBoard() }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Leaderboard", style = MaterialTheme.typography.headlineMedium)
                    Tag("🌍 Global", Tone.accent, Modifier.padding(top = 6.dp))
                }
                if (signedIn) {
                    Box(
                        Modifier.size(40.dp).clip(MaterialTheme.shapes.small).clickable { vm.loadBoard() },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(R.drawable.ic_refresh, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            Segmented(Period.entries.map { it.label }, Period.entries.indexOf(board.period), { vm.loadBoard(Period.entries[it]) })
        }
        item {
            when {
                !signedIn -> Panel {
                    EmptyNote("Sign in to compete", figure = app.fitnova.domain.Exercise.HIGH_KNEES)
                    PrimaryButton("Sign in", onClick = { vm.tab = Tab.PROFILE })
                }
                board.rows.isEmpty() && board.loading -> Panel(padding = PaddingValues(0.dp)) { repeat(5) { PlaceholderRow() } }
                board.rows.isEmpty() && board.error != null -> Panel {
                    EmptyNote("Couldn't load", board.error, emoji = "📡")
                    SecondaryButton("Try again", onClick = { vm.loadBoard() })
                }
                board.rows.isEmpty() -> Panel { EmptyNote("No scores yet", emoji = "🏁") }
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val me = Graph.session.userId
                    val rest = if (board.rows.size >= 3) {
                        Podium(board.rows.take(3), me)
                        board.rows.drop(3)
                    } else {
                        board.rows
                    }
                    if (rest.isNotEmpty()) {
                        Panel(padding = PaddingValues(0.dp)) {
                            rest.forEachIndexed { i, r ->
                                RankRow(r, me = r.userId == me)
                                if (i < rest.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
        if (signedIn) {
            item {
                Text(
                    if (board.updatedAt > 0) "Updated ${DateUtils.getRelativeTimeSpanString(board.updatedAt)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RankRow(r: Rank, me: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (me) Tone.accent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp)) {
            Text("${r.rank}", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
        }
        if (r.rank in 1..3) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Medals[r.rank - 1]))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (me) "${r.name} (you)" else r.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (me) FontWeight.SemiBold else FontWeight.Medium),
            )
            Text("${r.workouts} workouts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${r.points}", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold))
        Text(" pts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Top three as a podium: 2nd, 1st, 3rd, with flat medal-tinted blocks. */
@Composable
private fun Podium(top: List<Rank>, me: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        listOf(1, 0, 2).forEach { i ->
            val r = top[i]
            val medal = Medals[i]
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(if (i == 0) 56.dp else 48.dp).clip(CircleShape).background(medal.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text(r.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = medal) }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (r.userId == me) "You" else r.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text("${r.points} pts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(listOf(110.dp, 84.dp, 64.dp)[i])
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(medal.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text("${r.rank}", style = MaterialTheme.typography.headlineSmall, color = medal, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

/** Grey bars in place of rows while the first load is in flight (no shimmer: cheaper and calmer). */
@Composable
private fun PlaceholderRow() {
    val c = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 18.dp, height = 12.dp).clip(MaterialTheme.shapes.extraSmall).background(c))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f).height(12.dp).clip(MaterialTheme.shapes.extraSmall).background(c))
        Spacer(Modifier.width(40.dp))
        Box(Modifier.size(width = 36.dp, height = 12.dp).clip(MaterialTheme.shapes.extraSmall).background(c))
    }
}
