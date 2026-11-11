package app.fitnova.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.fitnova.AppViewModel
import app.fitnova.BoardState
import app.fitnova.Graph
import app.fitnova.GroupsState
import app.fitnova.Period
import app.fitnova.R
import app.fitnova.Scope
import app.fitnova.data.Group
import app.fitnova.data.Rank

private val ScopeLabels = Scope.entries.map { it.label }
internal val RankPeriods = Period.entries.map { it.label }

@Composable
fun RanksScreen(vm: AppViewModel, padding: PaddingValues) {
    val signedIn = vm.username != null
    // A tab visit reuses lists fetched in the last 2 minutes; the refresh button forces a reload.
    LaunchedEffect(signedIn) { if (signedIn) vm.refreshRanks() }
    var creating by rememberSaveable { mutableStateOf<Boolean?>(null) } // null = closed, true = club, false = group
    var joining by rememberSaveable { mutableStateOf(false) }
    val open = vm.openGroup
    // Creating or joining ends in the group's own sheet, which takes over from the form.
    LaunchedEffect(open) {
        if (open != null) {
            creating = null
            joining = false
        }
    }
    val me = remember(vm.username) { Graph.session.userId }
    val lists = vm.groups
    val myGroups = remember(lists.mine) { lists.mine.filter { !it.isCommunity } }
    val gutter = socialGutter()
    val create = { club: Boolean ->
        vm.clearGroupNote()
        creating = club
    }
    val join = {
        vm.clearGroupNote()
        joining = true
    }
    val openGroup = { g: Group ->
        vm.clearGroupNote()
        vm.openGroup(g)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        ranksItem("header") { RanksHeader(vm, signedIn) }
        if (!signedIn) {
            ranksItem("sign-in") { SignInCard(vm::openProfile) }
            return@LazyColumn
        }
        ranksItem("scope") { Segmented(ScopeLabels, Scope.entries.indexOf(vm.scope), { vm.scope = Scope.entries[it] }) }
        when (vm.scope) {
            Scope.GLOBAL -> {
                ranksItem("period") { Segmented(RankPeriods, Period.entries.indexOf(vm.board.period), { vm.loadBoard(Period.entries[it]) }) }
                rankBoard(vm.board, me, showYou = true, onRetry = { vm.loadBoard() })
            }
            Scope.GROUPS -> groupScope(
                lists, myGroups, vm.groupBusy, openGroup, onJoin = null, onRetry = vm::loadGroups,
                actions = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SocialPill("Create", { create(false) }, Modifier.weight(1f), icon = R.drawable.ic_plus)
                        SocialPill("Join", join, Modifier.weight(1f), icon = R.drawable.ic_key)
                    }
                },
                empty = {
                    Panel {
                        EmptyState("No groups yet", body = "Train with friends", illu = Illu.GROUPS_EMPTY)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SocialPill("Create", { create(false) }, Modifier.weight(1f), icon = R.drawable.ic_plus)
                            SocialPill("Join", join, Modifier.weight(1f), icon = R.drawable.ic_key)
                        }
                    }
                },
            )
            Scope.COMMUNITIES -> groupScope(
                lists, lists.communities, vm.groupBusy, openGroup, onJoin = vm::joinClub, onRetry = vm::loadGroups,
                actions = { SocialPill("New club", { create(true) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus) },
                empty = {
                    Panel { EmptyState("No clubs yet", body = "Start one for everyone", illu = Illu.GROUPS_EMPTY, action = "New club", onAction = { create(true) }) }
                },
            )
        }
    }

    open?.let { GroupSheet(vm, it, me) }
    creating?.let { club ->
        CreateGroupSheet(vm, club) {
            creating = null
            vm.clearGroupNote()
        }
    }
    if (joining) {
        JoinGroupSheet(vm) {
            joining = false
            vm.clearGroupNote()
        }
    }
}

/** A full-width item with the 12 dp gap under it. Rank and group rows skip it and sit flush, as one card. */
internal fun LazyListScope.ranksItem(key: String, content: @Composable () -> Unit) =
    item(key = key, contentType = key) { Box(Modifier.padding(bottom = 12.dp)) { content() } }

/** "Updated 5m ago" over the title, a refresh button that turns into a spinner, and the avatar. */
@Composable
private fun RanksHeader(vm: AppViewModel, signedIn: Boolean) {
    val board = vm.board
    val loading = board.loading || vm.groups.loading
    val now = rememberMinuteClock()
    val status = when {
        !signedIn -> null
        loading -> "Updating…"
        board.updatedAt > 0 -> "Updated ${updatedAgo(board.updatedAt, now)}"
        else -> null
    }
    ScreenHeader("Ranks", subtitle = status) {
        if (signedIn) RefreshButton(loading) { vm.refreshRanks(force = true) }
        ProfileButton(vm.username, vm::openProfile)
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onRefresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (loading) {
        Box(Modifier.size(48.dp).semantics { contentDescription = "Refreshing" }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.5.dp)
        }
    } else {
        IconCircleButton(R.drawable.ic_refresh, "Refresh", onRefresh, container = Color.Transparent, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    HeroPanel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Illustration(Illu.PODIUM, height = 180.dp)
            Spacer(Modifier.height(16.dp))
            Text("Sign in to compete", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "Weekly boards, groups and clubs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Sign in", onSignIn)
        }
    }
}

/** The board: your standing, a podium for the top three, then one lazy row per person. Shared by the group sheet. */
internal fun LazyListScope.rankBoard(board: BoardState, me: String?, showYou: Boolean, onRetry: () -> Unit) {
    val rows = board.rows
    val error = board.error
    when {
        rows.isEmpty() && board.loading -> ranksItem("board-wait") { PlaceholderCard(5) }
        rows.isEmpty() && error != null -> ranksItem("board-error") { RanksError(error, onRetry) }
        rows.isEmpty() -> ranksItem("board-empty") { Panel { EmptyState("No scores yet", body = "Train to claim #1", illu = Illu.PODIUM) } }
        else -> {
            if (error != null) ranksItem("board-stale") { StaleNote(error, onRetry) }
            if (showYou) ranksItem("you") { YouCard(rows, me) }
            // Group boards list members on 0 points too (all tied at #1), so the podium waits for three scorers.
            val start = if (rows.size >= 3 && rows[2].points > 0) 3 else 0
            if (start > 0) ranksItem("podium") { Podium(rows, me) }
            val count = rows.size - start
            items(count, key = { rows[start + it].userId }, contentType = { "rank" }) { i ->
                val r = rows[start + i]
                RankRow(r, r.userId == me, i, count)
            }
        }
    }
}

private fun LazyListScope.groupScope(
    state: GroupsState,
    list: List<Group>,
    busy: String?,
    onOpen: (Group) -> Unit,
    onJoin: ((Group) -> Unit)?,
    onRetry: () -> Unit,
    actions: @Composable () -> Unit,
    empty: @Composable () -> Unit,
) {
    val error = state.error
    when {
        list.isEmpty() && state.loading -> ranksItem("groups-wait") { PlaceholderCard(3) }
        list.isEmpty() && error != null -> ranksItem("groups-error") { RanksError(error, onRetry) }
        list.isEmpty() -> ranksItem("groups-empty") { empty() }
        else -> {
            ranksItem("groups-actions") { actions() }
            if (error != null) ranksItem("groups-stale") { StaleNote(error, onRetry) }
            items(list.size, key = { list[it].id }, contentType = { "group" }) { i ->
                GroupRow(list[i], i, list.size, busy, onOpen, onJoin)
            }
        }
    }
}

/** Where you stand: rank, points and the gap to the next place, or why you aren't listed. */
@Composable
private fun YouCard(rows: List<Rank>, me: String?) {
    val s = remember(rows, me) { boardStanding(rows, me) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val mine = s.mine
    val ahead = s.ahead
    val line = when {
        mine == null -> if (s.full) "Not in top $BOARD_LIMIT" else "Sync a workout to appear"
        ahead == null -> "Top of the board"
        else -> "${ahead.points - mine.points} pts to #${ahead.rank}"
    }
    val spoken = when {
        mine == null -> "Your rank: $line"
        ahead == null -> "Your rank ${mine.rank}, ${mine.points} points, top of the board"
        else -> "Your rank ${mine.rank}, ${mine.points} points, ${ahead.points - mine.points} points behind rank ${ahead.rank}"
    }
    Panel(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .heightIn(min = 56.dp)
                    .widthIn(min = 56.dp)
                    .background(colors.primary.copy(alpha = 0.14f), MaterialTheme.shapes.medium)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        mine != null -> "#${mine.rank}"
                        s.full -> "$BOARD_LIMIT+"
                        else -> "–"
                    },
                    style = type.headlineSmall,
                    color = colors.primary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("You", style = type.labelLarge, color = colors.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        mine == null -> Unit
                        ahead == null -> Emoji(R.drawable.emoji_trophy, 18.dp, Modifier.padding(end = 6.dp))
                        else -> Glyph(R.drawable.ic_trend_up, Modifier.padding(end = 6.dp), tint = colors.primary, size = 18)
                    }
                    Text(
                        line,
                        style = type.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (mine != null) {
                Spacer(Modifier.width(8.dp))
                Points(mine.points, big = true)
            }
        }
    }
}

private val MedalArt = intArrayOf(R.drawable.emoji_medal_1, R.drawable.emoji_medal_2, R.drawable.emoji_medal_3)
private val MedalTint = listOf(Nova.Gold, Nova.Silver, Nova.Bronze)
private val StepHeight = listOf(88.dp, 64.dp, 48.dp)
private val StepShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

/** 2-1-3 podium. Medal, colour and step height follow the rank, so a tie shares them; a crown sits on every #1. */
@Composable
private fun Podium(rows: List<Rank>, me: String?) {
    val spots = remember(rows) { podium(rows) }
    Panel(padding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            for ((r, medal) in spots) PodiumSpot(r, medal, r.userId == me, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PodiumSpot(r: Rank, medal: Int, mine: Boolean, modifier: Modifier) {
    val tint = MedalTint[medal]
    Column(
        modifier.clearAndSetSemantics { contentDescription = "Rank ${r.rank}, ${if (mine) "you" else r.name}, ${r.points} points" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (r.rank == 1) Emoji(R.drawable.emoji_crown, 28.dp)
        Avatar(r.name, if (medal == 0) 64.dp else 56.dp, tint = tint, ring = mine)
        Spacer(Modifier.height(6.dp))
        Text(
            if (mine) "You" else r.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Points(r.points)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(StepHeight[medal]).background(tint.copy(alpha = 0.16f), StepShape),
            contentAlignment = Alignment.TopCenter,
        ) { Emoji(MedalArt[medal], 32.dp, Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun RankRow(r: Rank, mine: Boolean, index: Int, count: Int) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .cardSegment(index, count, colors.surfaceContainer, colors.outlineVariant, divider = 104.dp, highlight = if (mine) colors.primary.copy(alpha = 0.08f) else null)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription = "Rank ${r.rank}, ${if (mine) "you" else r.name}, ${r.points} points, ${workoutCount(r.workouts)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            // Without a podium the top scorers still get their medal; nobody gets one for 0 points.
            if (r.rank in 1..3 && r.points > 0) {
                Emoji(MedalArt[r.rank - 1], 24.dp)
            } else {
                Text("${r.rank}", style = type.labelLarge, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Avatar(r.name, 40.dp, tint = if (mine) colors.primary else colors.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = type.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (mine) {
                    Spacer(Modifier.width(6.dp))
                    Tag("You", colors.primary)
                }
            }
            Text(workoutCount(r.workouts), style = type.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Points(r.points)
    }
}

/** A number and its "pts" unit on one baseline. */
@Composable
private fun Points(points: Int, big: Boolean = false) {
    val type = MaterialTheme.typography
    Row {
        Text(
            "$points",
            style = if (big) type.headlineSmall else type.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(" pts", style = type.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun GroupRow(g: Group, index: Int, count: Int, busy: String?, onOpen: (Group) -> Unit, onJoin: ((Group) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(segmentShape(index, count))
            .cardSegment(index, count, colors.surfaceContainer, colors.outlineVariant, divider = 72.dp)
            .clickable { onOpen(g) }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GroupBadge(g.emoji, 44.dp)
        Column(
            Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = "${g.name}, ${memberCount(g.members)}${if (g.isOwner) ", yours" else ""}"
            },
        ) {
            Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_users, tint = colors.onSurfaceVariant, size = 16)
                Spacer(Modifier.width(4.dp))
                Text("${g.members}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                if (g.isOwner) {
                    Spacer(Modifier.width(8.dp))
                    Glyph(R.drawable.ic_crown, tint = colors.onSurfaceVariant, size = 16)
                }
            }
        }
        when {
            onJoin == null -> Glyph(R.drawable.ic_chevron_right, tint = colors.onSurfaceVariant)
            g.joined -> Tag("Joined", Nova.colors.good, dot = true)
            else -> SocialPill("Join", { onJoin(g) }, enabled = busy == null, loading = busy == g.id)
        }
    }
}

/** Grey bars in place of rows while the first load runs (static: no shimmer). */
@Composable
private fun PlaceholderCard(rows: Int) {
    val bar = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val shape = MaterialTheme.shapes.extraSmall
    Panel(Modifier.clearAndSetSemantics { contentDescription = "Loading" }, padding = PaddingValues(vertical = 4.dp)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(bar, CircleShape))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).height(12.dp).background(bar, shape))
                Spacer(Modifier.width(48.dp))
                Box(Modifier.size(width = 36.dp, height = 12.dp).background(bar, shape))
            }
        }
    }
}

private fun isOffline(error: String) = error.contains("offline", ignoreCase = true)

/** Nothing to show and the load failed. The API's offline text talks about workouts, so it gets a board-sized title instead. */
@Composable
private fun RanksError(error: String, onRetry: () -> Unit) {
    val offline = isOffline(error)
    Panel {
        EmptyState(
            if (offline) "You're offline" else "Couldn't load",
            body = if (offline) null else error,
            illu = Illu.OFFLINE,
            action = "Retry",
            onAction = onRetry,
        )
    }
}

/** Older rows are still on screen but the refresh failed. */
@Composable
private fun StaleNote(error: String, onRetry: () -> Unit) {
    val warn = Nova.colors.warn
    val offline = isOffline(error)
    Row(
        Modifier.fillMaxWidth().background(warn.copy(alpha = 0.14f), MaterialTheme.shapes.medium).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(if (offline) R.drawable.ic_cloud_off else R.drawable.ic_warning, tint = warn)
        Spacer(Modifier.width(12.dp))
        Text(
            if (offline) "Offline" else "Couldn't refresh",
            style = MaterialTheme.typography.labelLarge,
            color = warn,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry", style = MaterialTheme.typography.labelLarge) }
    }
}

private val SegmentOnly = RoundedCornerShape(24.dp)
private val SegmentFirst = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val SegmentLast = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/** Clip for a clickable [cardSegment], so its ripple keeps the card's corners. */
private fun segmentShape(index: Int, count: Int): Shape = when {
    count == 1 -> SegmentOnly
    index == 0 -> SegmentFirst
    index == count - 1 -> SegmentLast
    else -> RectangleShape
}

/**
 * One row of a card that is split into lazy items: the card fill and 1 dp hairline, rounded only at the ends,
 * and one divider (inset by [divider]) between rows. Paths come from drawWithCache, so scrolling allocates nothing.
 */
private fun Modifier.cardSegment(index: Int, count: Int, fill: Color, edge: Color, divider: Dp, highlight: Color? = null) = drawWithCache {
    val w = 1.dp.toPx()
    val radius = 24.dp.toPx()
    val first = index == 0
    val last = index == count - 1
    fun box(inset: Float): Path {
        val top = CornerRadius((if (first) radius - inset else 0f).coerceAtLeast(0f))
        val bottom = CornerRadius((if (last) radius - inset else 0f).coerceAtLeast(0f))
        return Path().apply { addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, top, top, bottom, bottom)) }
    }
    val area = box(0f)
    val outline = box(w / 2f) // inside the bounds, like Modifier.border
    val stroke = Stroke(w)
    val cut = divider.toPx()
    onDrawBehind {
        drawPath(area, fill)
        if (highlight != null) drawPath(area, highlight)
        // The upper row draws the line between two rows (inset at the leading side); the lower row drops its top edge.
        val rtl = layoutDirection == LayoutDirection.Rtl
        val insetLeft = if (rtl) size.width - cut else w
        val insetRight = if (rtl) size.width - w else cut
        clipRect(w, 0f, size.width - w, if (first) 0f else w, ClipOp.Difference) {
            clipRect(insetLeft, size.height - w, insetRight, if (last) size.height - w else size.height, ClipOp.Difference) {
                drawPath(outline, edge, style = stroke)
            }
        }
    }
}
