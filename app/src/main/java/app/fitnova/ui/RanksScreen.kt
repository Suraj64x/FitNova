package app.fitnova.ui

import android.text.format.DateUtils
import android.content.Intent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import app.fitnova.BoardState
import app.fitnova.GroupsState
import app.fitnova.Scope
import app.fitnova.data.Group
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
private val MedalEmoji = listOf("🥇", "🥈", "🥉")

@Composable
fun RanksScreen(vm: AppViewModel, padding: PaddingValues) {
    val signedIn = vm.username != null
    LaunchedEffect(signedIn) {
        if (signedIn) {
            vm.loadBoard()
            vm.loadGroups()
        }
    }
    var creating by rememberSaveable { mutableStateOf<Boolean?>(null) } // null = closed, true = club, false = group
    var joining by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Leaderboard", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (signedIn) {
                    Box(
                        Modifier.size(40.dp).clip(MaterialTheme.shapes.small).clickable {
                            vm.loadBoard()
                            vm.loadGroups()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(R.drawable.ic_refresh, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            val emoji = mapOf(Scope.GLOBAL to "🌍", Scope.GROUPS to "👥", Scope.COMMUNITIES to "🏘️")
            Segmented(Scope.entries.map { "${emoji[it]} ${it.label}" }, Scope.entries.indexOf(vm.scope), { vm.scope = Scope.entries[it] })
        }
        if (!signedIn) {
            item {
                Panel {
                    EmptyNote("Sign in to compete", figure = app.fitnova.domain.Exercise.HIGH_KNEES)
                    PrimaryButton("Sign in", onClick = { vm.tab = Tab.PROFILE })
                }
            }
            return@LazyColumn
        }
        when (vm.scope) {
            Scope.GLOBAL -> {
                item { Segmented(Period.entries.map { it.label }, Period.entries.indexOf(vm.board.period), { vm.loadBoard(Period.entries[it]) }) }
                item { Board(vm.board, onRetry = { vm.loadBoard() }) }
            }
            Scope.GROUPS -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("＋ Create", onClick = { creating = false }, modifier = Modifier.weight(1f))
                        SecondaryButton("🔑 Join", onClick = { joining = true }, modifier = Modifier.weight(1f))
                    }
                }
                val mine = vm.groups.mine.filter { !it.isCommunity }
                item { GroupList(vm.groups, mine, empty = "No groups yet" to "👥", onOpen = vm::openGroup, onJoin = null) }
            }
            Scope.COMMUNITIES -> {
                item { SecondaryButton("＋ New club", onClick = { creating = true }) }
                item { GroupList(vm.groups, vm.groups.communities, empty = "No clubs yet" to "🏘️", onOpen = vm::openGroup, onJoin = vm::joinCommunity) }
            }
        }
    }

    vm.openGroup?.let { g ->
        GroupSheet(
            group = g,
            board = vm.groupBoard,
            onPeriod = vm::loadGroupBoard,
            onJoin = if (g.isCommunity && vm.groups.communities.none { it.id == g.id && it.joined }) ({ vm.joinCommunity(g) }) else null,
            onLeave = { vm.leaveGroup(g) },
            onDismiss = vm::closeGroup,
        )
    }
    creating?.let { club ->
        CreateGroupDialog(club, onCreate = { name, emoji -> creating = null; vm.createGroup(name, club, emoji) }, onDismiss = { creating = null })
    }
    if (joining) JoinDialog(onJoin = { code -> joining = false; vm.joinGroup(code) }, onDismiss = { joining = false })
}

/** Podium for the top three, then the rest as rows. Shared by the global and group boards. */
@Composable
private fun Board(board: BoardState, onRetry: () -> Unit) {
    val me = Graph.session.userId
    when {
        board.rows.isEmpty() && board.loading -> Panel(padding = PaddingValues(0.dp)) { repeat(5) { PlaceholderRow() } }
        board.rows.isEmpty() && board.error != null -> Panel {
            EmptyNote("Couldn't load", board.error, emoji = "📡")
            SecondaryButton("Try again", onClick = onRetry)
        }
        board.rows.isEmpty() -> Panel { EmptyNote("No scores yet", emoji = "🏁") }
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MyRank(board.rows, me)
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
            if (board.updatedAt > 0) {
                Text(
                    "Updated ${DateUtils.getRelativeTimeSpanString(board.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupList(state: GroupsState, list: List<Group>, empty: Pair<String, String>, onOpen: (Group) -> Unit, onJoin: ((Group) -> Unit)?) {
    when {
        list.isEmpty() && state.loading -> Panel(padding = PaddingValues(0.dp)) { repeat(3) { PlaceholderRow() } }
        list.isEmpty() && state.error != null -> Panel { EmptyNote("Couldn't load", state.error, emoji = "📡") }
        list.isEmpty() -> Panel { EmptyNote(empty.first, emoji = empty.second) }
        else -> Panel(padding = PaddingValues(0.dp)) {
            list.forEachIndexed { i, g ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(g) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(46.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text(g.emoji, style = MaterialTheme.typography.titleLarge) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("👤 ${g.members}${if (g.isOwner) " · 👑" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        onJoin != null && !g.joined -> Chip("Join", selected = true, onClick = { onJoin(g) })
                        onJoin != null -> Tag("✓ Joined", Tone.good)
                        else -> Glyph(R.drawable.ic_chevron, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18)
                    }
                }
                if (i < list.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSheet(
    group: Group,
    board: BoardState,
    onPeriod: (Period) -> Unit,
    onJoin: (() -> Unit)?,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmLeave by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(60.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text(group.emoji, style = MaterialTheme.typography.headlineMedium) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(group.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${if (group.isCommunity) "🏘️ Club" else "👥 Group"} · 👤 ${group.members}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            group.inviteCode?.let { code ->
                Panel(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🔑 Invite code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(code, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp))
                        }
                        Chip("Share", selected = true, onClick = {
                            val text = "Join my Fit Nova group \"${group.name}\" with code $code"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        })
                    }
                }
            }
            if (onJoin != null) PrimaryButton("Join club", onClick = onJoin)
            Segmented(Period.entries.map { it.label }, Period.entries.indexOf(board.period), { onPeriod(Period.entries[it]) })
            Board(board, onRetry = { onPeriod(board.period) })
            if (onJoin == null) SecondaryButton("Leave", onClick = { confirmLeave = true }, color = Tone.bad)
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Leave ${group.name}?") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onLeave() }) { Text("Leave", color = Tone.bad) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
}

private val GroupEmoji = listOf("💪", "🔥", "🏃", "🧘", "⚡", "🌱", "🏆", "🚴")

@Composable
private fun CreateGroupDialog(club: Boolean, onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(GroupEmoji.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(if (club) "New club" else "New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GroupEmoji.forEach { e ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (e == emoji) Tone.accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.trim().length >= 2, onClick = { onCreate(name, emoji) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun JoinDialog(onJoin: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("🔑 Join a group") },
        text = {
            OutlinedTextField(
                code,
                { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                label = { Text("Invite code") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = code.length == 6, onClick = { onJoin(code) }) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
        Box(Modifier.width(34.dp)) {
            Text(
                MedalEmoji.getOrNull(r.rank - 1) ?: "${r.rank}",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Avatar(r.name, if (me) Tone.accent else MaterialTheme.colorScheme.onSurfaceVariant, 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (me) "${r.name} · you" else r.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (me) FontWeight.SemiBold else FontWeight.Medium),
            )
            Text("🏋️ ${r.workouts}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(if (i == 0) "👑" else " ", style = MaterialTheme.typography.titleLarge)
                Avatar(r.name, medal, if (i == 0) 60 else 50, ring = r.userId == me)
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
                    Text(MedalEmoji[i], style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
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

/** Initial in a tinted circle; [ring] outlines it (used for "you"). */
@Composable
private fun Avatar(name: String, tone: Color, size: Int, ring: Boolean = false) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.16f))
            .then(if (ring) Modifier.border(2.dp, Tone.accent, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(name.take(1).uppercase(), style = if (size >= 48) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall, color = tone) }
}

/** Where you stand: your rank, points, and how far the next place is. */
@Composable
private fun MyRank(rows: List<Rank>, me: String?) {
    val mine = rows.firstOrNull { it.userId == me }
    Panel(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(Tone.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (mine == null) "–" else "#${mine.rank}", style = MaterialTheme.typography.titleLarge, color = Tone.accent)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Your rank", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (mine == null) {
                    Text("Log a workout to join", style = MaterialTheme.typography.titleSmall)
                } else {
                    val ahead = rows.lastOrNull { it.points > mine.points }
                    Text(
                        when {
                            mine.rank == 1 -> "🏆 Top of the board"
                            ahead != null -> "⬆️ ${ahead.points - mine.points} pts to #${ahead.rank}"
                            else -> "Keep going"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (mine != null) {
                Text("${mine.points}", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
                Text(" pts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
