package app.fitnova.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitnova.R
import app.fitnova.domain.Category
import app.fitnova.domain.Exercise

// Flat everywhere: depth comes from tone and 1 dp hairlines, never elevation, shadow or gradients.
// Decorative fills use background(color, shape); clip() only where a ripple or image needs it.

// ---- Cards and headers ----

/** Flat card: surfaceContainer, 1 dp outlineVariant hairline, large shape. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        // The single clip keeps ripples and full-width row highlights inside the rounded corners.
        modifier
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The one hero card of a screen: extraLarge shape, 20 dp padding. */
@Composable
fun HeroPanel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    Panel(modifier, onClick, PaddingValues(20.dp), MaterialTheme.shapes.extraLarge, content)

/**
 * Tab header row: a small [subtitle] line above the [title] (e.g. the date above the greeting), and a
 * [trailing] slot, usually [ProfileButton]. Adds no status-bar inset.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        trailing()
    }
}

/** 40 dp avatar in a 48 dp touch target that opens Profile. [name] null shows a generic user icon. */
@Composable
fun ProfileButton(name: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Profile" },
        contentAlignment = Alignment.Center,
    ) { Avatar(name, 40.dp, modifier = Modifier.clearAndSetSemantics {}) }
}

/** Header for full-screen pages (Profile, History): back arrow, title, [actions]. Adds no status-bar inset. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(R.drawable.ic_back, "Back", onBack, container = Color.Transparent)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
        )
        actions()
    }
}

/** Section title with optional emoji art. [action] is a real button only when [onAction] is set, else a plain label. */
@Composable
fun SectionTitle(
    text: String,
    @DrawableRes emoji: Int? = null,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (emoji != null) {
            Emoji(emoji, 24.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        when {
            action == null -> Unit
            onAction != null -> TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
            else -> Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Icons, artwork, illustrations ----

/** A plain vector icon. Pass [contentDescription] when nothing next to it says what it means. */
@Composable
fun Glyph(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Int = 20,
    contentDescription: String? = null,
) {
    Icon(painterResource(res), contentDescription, modifier.size(size.dp), tint = tint)
}

/** Semantic icon (use the `ic_*_duo` duotone weight) in [tint] on the same colour at 14 %. */
@Composable
fun IconTile(@DrawableRes icon: Int, tint: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(tint.copy(alpha = 0.14f), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, Modifier.size(size * 0.6f), tint = tint) }
}

/** Fluent artwork on a tonal disc ([tint] at 14 %, else surfaceVariant), so its dark parts stay visible in dark mode. */
@Composable
fun EmojiTile(@DrawableRes emoji: Int, tint: Color? = null, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(tint?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Emoji(emoji, size * 0.6f) }
}

/** Fluent emoji artwork (`emoji_*`), never tinted. Decorative: pair it with a text label. */
@Composable
fun Emoji(@DrawableRes res: Int, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Image(painterResource(res), contentDescription = null, modifier = modifier.size(size))
}

/** The 14 bundled scenes, each with a light and a dark recolour. */
enum class Illu(@param:DrawableRes val light: Int, @param:DrawableRes val dark: Int) {
    WELCOME(R.drawable.illu_welcome, R.drawable.illu_welcome_dark),
    CAMERA_COACH(R.drawable.illu_camera_coach, R.drawable.illu_camera_coach_dark),
    CELEBRATE(R.drawable.illu_celebrate, R.drawable.illu_celebrate_dark),
    NO_WORKOUTS(R.drawable.illu_no_workouts, R.drawable.illu_no_workouts_dark),
    OFFLINE(R.drawable.illu_offline, R.drawable.illu_offline_dark),
    PODIUM(R.drawable.illu_podium, R.drawable.illu_podium_dark),
    ROUTINE(R.drawable.illu_routine, R.drawable.illu_routine_dark),
    SLEEP(R.drawable.illu_sleep, R.drawable.illu_sleep_dark),
    ALARM_MORNING(R.drawable.illu_alarm_morning, R.drawable.illu_alarm_morning_dark),
    CLOUD_SYNC(R.drawable.illu_cloud_sync, R.drawable.illu_cloud_sync_dark),
    GROUPS_EMPTY(R.drawable.illu_groups_empty, R.drawable.illu_groups_empty_dark),
    HISTORY_EMPTY(R.drawable.illu_history_empty, R.drawable.illu_history_empty_dark),
    SIGN_IN(R.drawable.illu_sign_in, R.drawable.illu_sign_in_dark),
    STATS(R.drawable.illu_stats, R.drawable.illu_stats_dark),
}

/** Decorative scene at a fixed [height]; the width follows the art. Picks the variant for the app theme. */
@Composable
fun Illustration(illu: Illu, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    Image(painterResource(if (LocalDark.current) illu.dark else illu.light), contentDescription = null, modifier = modifier.height(height))
}

/** Fluent artwork for a category: 💪 🦵 🧘 🏃. */
val Category.art: Int
    @DrawableRes get() = when (this) {
        Category.UPPER -> R.drawable.emoji_biceps
        Category.LOWER -> R.drawable.emoji_leg
        Category.CORE -> R.drawable.emoji_lotus
        Category.CARDIO -> R.drawable.emoji_runner
    }

// ---- Empty states ----

/** Empty state for inside a card: an illustration or emoji tile, a short title, one line and one tonal button. */
@Composable
fun EmptyState(
    title: String,
    body: String? = null,
    illu: Illu? = null,
    @DrawableRes emoji: Int? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) = EmptyLayout(
    title, body, modifier,
    art = {
        if (illu != null) {
            Illustration(illu, height = 140.dp)
            Spacer(Modifier.height(12.dp))
        } else if (emoji != null) {
            EmojiTile(emoji, size = 72.dp)
            Spacer(Modifier.height(12.dp))
        }
    },
    footer = {
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton(action, onAction)
        }
    },
)

/** 1.4 empty state with a text emoji or stick figure; prefer [EmptyState]. */
@Composable
fun EmptyNote(
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    figure: Exercise? = null,
) = EmptyLayout(
    title, body, modifier,
    art = {
        if (figure != null) ExerciseIcon(figure, Modifier.size(96.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        if (emoji != null) Text(emoji, style = MaterialTheme.typography.displayMedium)
        if (figure != null || emoji != null) Spacer(Modifier.height(8.dp))
    },
)

@Composable
private fun EmptyLayout(
    title: String,
    body: String?,
    modifier: Modifier,
    art: @Composable () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        art()
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        footer()
    }
}

// ---- Rows ----

/** List row, 64 dp min: optional leading tile, title plus one supporting line, optional trailing value. Read as one item. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) = RowLayout(
    modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.semantics(mergeDescendants = true) {}),
    title, subtitle, leading, trailing,
)

/** Settings switch row; the whole row toggles (role Switch), so TalkBack reads the title with the state. */
@Composable
fun ToggleRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) = RowLayout(
    modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    title, subtitle,
    leading = { IconTile(icon, tint) },
    trailing = { Switch(checked = checked, onCheckedChange = null) },
)

/** Settings row that opens something: icon tile, title, optional current [value] and a chevron. */
@Composable
fun NavRow(
    @DrawableRes icon: Int,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) = ListRow(
    title,
    leading = { IconTile(icon, tint) },
    trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.width(4.dp))
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },
    onClick = onClick,
    modifier = modifier,
)

@Composable
private fun RowLayout(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

// ---- Buttons ----

/** The one main action of a screen: 56 dp pill in primary. [loading] keeps the fill and shows a spinner. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = if (loading) colors.primary else colors.onSurface.copy(alpha = 0.12f),
            disabledContentColor = if (loading) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f),
        ),
        elevation = null,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = colors.onPrimary)
        } else {
            Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tonal 56 dp pill, no border. Pass `color = Nova.colors.bad` for a destructive action. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (LocalDark.current) colors.surfaceContainerHighest else colors.surfaceVariant,
            contentColor = color,
        ),
        elevation = null,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 48 dp round icon button; [contentDescription] is required because the icon is the only label. */
@Composable
fun IconCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}

// ---- Chips, tags, pills ----

/** Filter chip: 36 dp pill in a 48 dp target. Selected = inverseSurface (brand colour stays for CTAs and data). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, @DrawableRes emoji: Int? = null, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) colors.inverseSurface else colors.surfaceVariant)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) {
            Emoji(emoji, 20.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.inverseOnSurface else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Static label: [tone] at 14 % behind onSurface text; status inks (Nova good/warn/bad) and primary colour the text. */
@Composable
fun Tag(text: String, tone: Color = MaterialTheme.colorScheme.onSurfaceVariant, dot: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 24.dp)
            .background(tone.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).background(tone, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = inkOn(tone), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round status label: tint, optional dot and text (same ink rule as [Tag]). */
@Composable
fun Pill(text: String, tone: Color, modifier: Modifier = Modifier, dot: Boolean = true) {
    Row(
        modifier.background(tone.copy(alpha = 0.14f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).background(tone, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = inkOn(tone), maxLines = 1)
    }
}

/** Data colours never colour text; only primary and the status inks do (they pass 4.5:1 on their own tint). */
@Composable
@ReadOnlyComposable
private fun inkOn(tone: Color): Color {
    val n = Nova.colors
    val colors = MaterialTheme.colorScheme
    return if (tone == n.good || tone == n.warn || tone == n.bad || tone == colors.primary) tone else colors.onSurface
}

/** Pill track (up to 3 options) whose thumb slides in the draw phase; options are tabs for TalkBack. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val thumb = colors.surfaceBright
    val edge = colors.outlineVariant
    val position = animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.9f, stiffness = 1400f), label = "segment")
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant, CircleShape)
            .drawWithCache {
                val inset = 4.dp.toPx()
                val w = (size.width - inset * 2) / options.size.coerceAtLeast(1)
                val h = size.height - inset * 2
                val radius = CornerRadius(h / 2)
                val hairline = Stroke(1.dp.toPx())
                onDrawBehind {
                    val x = inset + w * position.value
                    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - x - w else x
                    drawRoundRect(thumb, Offset(left, inset), Size(w, h), radius)
                    drawRoundRect(edge, Offset(left, inset), Size(w, h), radius, style = hairline)
                }
            }
            .padding(4.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

// ---- Numbers and people ----

/**
 * Number-first card: optional emoji and label, one big tabular number, a small unit. Read as one item. The number
 * shrinks to fit (3-up on 320 dp, large fonts) rather than being cut to a wrong value.
 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, @DrawableRes emoji: Int? = null) {
    val type = MaterialTheme.typography
    Panel(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                Emoji(emoji, 20.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            // weight(fill = false) measures the unit first, so the number shrinks into what is left instead of pushing it out.
            Text(
                value,
                style = type.headlineSmall,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = type.labelLarge.fontSize, maxFontSize = type.headlineSmall.fontSize, stepSize = 1.sp),
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            )
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline().padding(start = 4.dp),
                )
            }
        }
    }
}

/** Initial on a 16 % [tint] (brand for "me", medal colours on the podium). [ring] = 2 dp primary ring with a 2 dp gap. */
@Composable
fun Avatar(
    name: String?,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    ring: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val initial = name?.trim()?.takeIf { it.isNotEmpty() }?.let { it.substring(0, Character.charCount(it.codePointAt(0))).uppercase() }
    Box(
        modifier
            .size(size)
            .then(if (ring) Modifier.border(2.dp, colors.primary, CircleShape).padding(4.dp) else Modifier)
            .background(if (initial != null) tint.copy(alpha = 0.16f) else colors.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                initial,
                style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
            )
        } else {
            Icon(painterResource(R.drawable.ic_user), null, Modifier.size(size * 0.5f), tint = colors.onSurfaceVariant)
        }
    }
}

// ---- Sheets and dialogs ----

/**
 * Bottom sheet styled to the system: surfaceContainerLow, 32 dp top corners, 20 dp padding above the nav bar.
 * [content] scrolls (items spaced 12 dp; pass `scrollable = false` to host a LazyColumn) while [footer], usually
 * the primary action, stays pinned. [message] shows a status line inside the sheet, where snackbars can't reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaSheet(
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    message: String? = null,
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (LocalDark.current) 0.6f else 0.4f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp, color = colors.outline.copy(alpha = 0.4f)) },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Column(
                Modifier.weight(1f, fill = false).then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inverseOnSurface,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .background(colors.inverseSurface, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (footer != null) {
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { footer() }
            }
        }
    }
}

/** Only for destructive confirms. Confirm runs [onConfirm], then [onDismiss] closes the dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String? = null,
    confirm: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, style = MaterialTheme.typography.labelLarge, color = if (destructive) Nova.colors.bad else colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant) }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = text?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) } },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colors.surfaceContainerHigh,
        tonalElevation = 0.dp,
    )
}

// ---- Inputs ----

/**
 * A number between labelled 48 dp − and + buttons, clamped to [range]. [step] gives the step size at a value;
 * going down uses the size at value − 1, so `step = { if (it < 20) 1 else 5 }` runs 19 → 20 → 25 and back 25 → 20 → 19.
 */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    step: (Int) -> Int = { 1 },
    range: IntRange = 0..Int.MAX_VALUE,
    unit: String? = null,
    format: (Int) -> String = { it.toString() },
    modifier: Modifier = Modifier,
) {
    val what = if (unit != null) " $unit" else ""
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(
            R.drawable.ic_minus, "Decrease$what",
            onClick = { onChange((value - step(value - 1)).coerceIn(range)) },
            enabled = value > range.first,
        )
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(format(value), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.alignByBaseline())
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline().padding(start = 4.dp),
                )
            }
        }
        IconCircleButton(
            R.drawable.ic_plus, "Increase$what",
            onClick = { onChange((value + step(value)).coerceIn(range)) },
            enabled = value < range.last,
        )
    }
}

/** Exercise stick figure on its category colour at 14 %. */
@Composable
fun ExerciseTile(exercise: Exercise, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).background(Nova.colors.of(exercise.category).copy(alpha = 0.14f), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) { ExerciseIcon(exercise, Modifier.size((size * 0.78f).dp)) }
}
