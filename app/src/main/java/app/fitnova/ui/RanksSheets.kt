package app.fitnova.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitnova.AppViewModel
import app.fitnova.Period
import app.fitnova.R
import app.fitnova.data.Group
import kotlinx.coroutines.delay

/** A group or club: header, invite code, its board, and Join or Leave. Results show inside the sheet. */
@Composable
internal fun GroupSheet(vm: AppViewModel, open: Group, me: String?) {
    val context = LocalContext.current
    val lists = vm.groups
    // The lists hold the fresh copy (member count, joined) once they reload; until then, the one that was opened.
    val g = remember(open, lists) { lists.mine.firstOrNull { it.id == open.id } ?: lists.communities.firstOrNull { it.id == open.id } ?: open }
    val joined = remember(open, lists) {
        open.joined || lists.mine.any { it.id == open.id } || lists.communities.any { it.id == open.id && it.joined }
    }
    val busy = vm.groupBusy
    var copies by remember { mutableIntStateOf(0) } // every copy restarts the "Copied" timer
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(2_000)
            copies = 0
        }
    }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val board = vm.groupBoard

    NovaSheet(
        onDismiss = {
            vm.closeGroup()
            vm.clearGroupNote()
        },
        footer = if (g.isCommunity && !joined) {
            { PrimaryButton("Join club", { vm.joinClub(g) }, enabled = busy == null, loading = busy == g.id) }
        } else {
            null
        },
        message = if (copies > 0) "Copied" else vm.groupNote,
        scrollable = false,
    ) {
        LazyColumn(Modifier.weight(1f, fill = false)) {
            ranksItem("head") { GroupHead(g) }
            g.inviteCode?.let { code ->
                ranksItem("code") {
                    InviteCodeRow(
                        code,
                        onCopy = {
                            copyText(context, "Invite code", code)
                            copies++
                        },
                        onShare = { shareInvite(context, g.name, code) },
                    )
                }
            }
            ranksItem("period") { Segmented(RankPeriods, Period.entries.indexOf(board.period), { vm.loadGroupBoard(Period.entries[it]) }) }
            rankBoard(board, me, showYou = false, onRetry = { vm.loadGroupBoard(board.period) })
            if (joined) {
                item("leave") {
                    SocialPill(
                        if (g.isCommunity) "Leave club" else "Leave group",
                        { confirmLeave = true },
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        icon = R.drawable.ic_sign_out,
                        color = Nova.colors.bad,
                        enabled = busy == null,
                        loading = busy == g.id,
                    )
                }
            }
        }
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave ${g.name}?",
            text = when {
                !g.isCommunity && g.members <= 1 -> "You're the last member, so the group is deleted."
                g.isOwner && g.members > 1 -> "The longest-standing member takes over."
                !g.isCommunity -> "You can rejoin with the code."
                else -> null
            },
            confirm = "Leave",
            destructive = true,
            onConfirm = { vm.leaveGroupAndClose(g) },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun GroupHead(g: Group) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        GroupBadge(g.emoji, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(if (g.isCommunity) R.drawable.ic_globe else R.drawable.ic_users, tint = colors.onSurfaceVariant, size = 16)
                Spacer(Modifier.width(4.dp))
                Text(
                    "${if (g.isCommunity) "Club" else "Group"} · ${memberCount(g.members)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** The code in big letters with Copy and Share. */
@Composable
private fun InviteCodeRow(code: String, onCopy: () -> Unit, onShare: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceVariant, MaterialTheme.shapes.large).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(R.drawable.ic_key, tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text("Invite code", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(code, style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp), maxLines = 1)
        }
        IconCircleButton(R.drawable.ic_copy, "Copy code", onCopy, container = Color.Transparent)
        IconCircleButton(R.drawable.ic_share, "Share invite", onShare, container = Color.Transparent)
    }
}

private fun shareInvite(context: Context, name: String, code: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, inviteText(name, code))
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Name plus an emoji from the picker. One request at a time; errors show in the sheet. */
@Composable
internal fun CreateGroupSheet(vm: AppViewModel, club: Boolean, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf(GroupArts.first().emoji) }
    val valid = name.trim().length in 2..40
    val submit = { if (valid) vm.createGroupAndOpen(name, club, emoji) }
    NovaSheet(
        onDismiss = onDismiss,
        footer = {
            PrimaryButton(
                if (club) "Create club" else "Create group",
                submit,
                enabled = valid && vm.groupBusy == null,
                loading = vm.groupBusy == "create",
            )
        },
        message = vm.groupNote,
    ) {
        Text(if (club) "New club" else "New group", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(40)
                vm.clearGroupNote()
            },
            label = { Text("Name") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        EmojiPicker(emoji) { emoji = it }
    }
}

@Composable
private fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in GroupArtRows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in row) {
                    val on = a.emoji == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(shape)
                            .background(if (on) colors.primary.copy(alpha = 0.14f) else colors.surfaceVariant)
                            .then(if (on) Modifier.border(2.dp, colors.primary, shape) else Modifier)
                            .selectable(on, role = Role.RadioButton, onClick = { onSelect(a.emoji) })
                            .semantics { contentDescription = a.label },
                        contentAlignment = Alignment.Center,
                    ) { Emoji(a.art, 32.dp) }
                }
            }
        }
    }
}

/** The code field accepts anything pasted (a whole invite message too) and keeps just the code. */
@Composable
internal fun JoinGroupSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var noCode by remember { mutableStateOf(false) } // a pasted text had no code in it
    val valid = isInviteCode(code)
    val wrong = code.length == 6 && !valid
    val submit = { if (valid) vm.joinGroupByCode(code) }
    NovaSheet(
        onDismiss = onDismiss,
        footer = { PrimaryButton("Join", submit, enabled = valid && vm.groupBusy == null, loading = vm.groupBusy == "join") },
        message = vm.groupNote,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(R.drawable.emoji_key, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Text("Join a group", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = code,
            onValueChange = { typed ->
                // Typing keeps up to 6 letters and digits; a longer paste only counts if it holds a code.
                val clean = typed.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
                val found = inviteCode(typed)
                val pasted = clean.length > 6 && typed.length > code.length + 1
                noCode = found == null && pasted
                code = found ?: if (pasted) code else clean.take(6)
                vm.clearGroupNote()
            },
            label = { Text("Invite code") },
            placeholder = { Text("Paste the invite") },
            singleLine = true,
            isError = wrong || noCode,
            supportingText = when {
                noCode -> ({ Text("No code in that text") })
                wrong -> ({ Text("Check the code") })
                else -> null
            },
            textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- Group emoji ----

/** Group emoji are stored as text; the picker's ones show as Fluent artwork. */
private class GroupArt(val emoji: String, @param:DrawableRes val art: Int, val label: String)

private val GroupArts = listOf(
    GroupArt("💪", R.drawable.emoji_biceps, "Muscle"),
    GroupArt("🔥", R.drawable.emoji_fire, "Fire"),
    GroupArt("🏃", R.drawable.emoji_runner, "Runner"),
    GroupArt("🧘", R.drawable.emoji_lotus, "Yoga"),
    GroupArt("⚡", R.drawable.emoji_bolt, "Bolt"),
    GroupArt("🌱", R.drawable.emoji_seedling, "Sprout"),
    GroupArt("🏆", R.drawable.emoji_trophy, "Trophy"),
    GroupArt("🚀", R.drawable.emoji_rocket, "Rocket"),
)
private val GroupArtRows = GroupArts.chunked(4)

/** A group's emoji on a tonal disc: artwork when it is one of ours, else the text emoji (older clients). */
@Composable
internal fun GroupBadge(emoji: String, size: Dp) {
    val bare = emoji.replace("️", "")
    val art = GroupArts.firstOrNull { it.emoji == bare }?.art
    if (art != null) {
        EmojiTile(art, size = size)
    } else {
        Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Text(emoji, style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
        }
    }
}

// ---- Shared by Ranks, Profile and Onboarding ----

/** Screen gutter: 16 dp, or 20 dp from 400 dp wide. */
@Composable
internal fun socialGutter(): Dp = if (LocalConfiguration.current.screenWidthDp >= 400) 20.dp else 16.dp

/** Compact tonal pill (40 dp tall, 48 dp touch) for actions inside rows and cards; [loading] shows a spinner and blocks taps. */
@Composable
internal fun SocialPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    color: Color = Color.Unspecified,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val base = if (color == Color.Unspecified) colors.onSurface else color
    val ink = if (enabled || loading) base else base.copy(alpha = 0.38f)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (LocalDark.current) colors.surfaceContainerHighest else colors.surfaceVariant)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The label stays laid out (and read by TalkBack) under the spinner, so the pill keeps its width.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shown = if (loading) Color.Transparent else ink
            if (icon != null) {
                Icon(painterResource(icon), null, Modifier.size(18.dp), tint = shown)
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = shown, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = base, strokeWidth = 2.dp)
    }
}

/** The current time, ticking once a minute, for "5m ago" labels. Read it in a small composable so only that redraws. */
@Composable
internal fun rememberMinuteClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** Copies to the clipboard; [sensitive] keeps it out of the clipboard preview (Android 13+). */
internal fun copyText(context: Context, label: String, text: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    runCatching { context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip) }
}

/** Opens a web page; does nothing on a phone without a browser. */
internal fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
