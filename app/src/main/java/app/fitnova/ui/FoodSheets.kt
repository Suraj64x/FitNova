package app.fitnova.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fitnova.R
import app.fitnova.domain.AGE_RANGE
import app.fitnova.domain.Activity
import app.fitnova.domain.Body
import app.fitnova.domain.Goal
import app.fitnova.domain.HEIGHT_RANGE
import app.fitnova.domain.Recipe
import app.fitnova.domain.Sex
import app.fitnova.domain.Targets
import app.fitnova.domain.WEIGHT_RANGE
import app.fitnova.domain.bodyOrNull
import app.fitnova.domain.foodArt
import app.fitnova.domain.stillPossible
import app.fitnova.domain.targets

private val KcalRange = 1..9999
private val GramRange = 0..999

// ---- Recipe ----

/** Recipe details: photo, macros, an ingredients checklist, numbered steps and credits. "Log meal" stays pinned. */
@Composable
fun RecipeSheet(r: Recipe, onLog: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val n = Nova.colors
    val colors = MaterialTheme.colorScheme
    val checked = remember(r.id) { mutableStateListOf<Int>() }
    var note by remember(r.id) { mutableStateOf<String?>(null) }
    val photoWidth = LocalWindowInfo.current.containerSize.width.takeIf { it > 0 } ?: 720
    val photoHeight = with(LocalDensity.current) { 200.dp.roundToPx() }
    val open = { url: String -> if (!openLink(context, url)) note = "No app here can open links" }

    NovaSheet(onDismiss, footer = { PrimaryButton("Log meal · ${r.kcal} kcal", onLog) }, message = note) {
        r.photo?.let {
            AssetPhoto(it, photoWidth, Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large), targetHeightPx = photoHeight)
        }
        Text(r.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaTag("${r.minutes} min") { Glyph(R.drawable.ic_clock, tint = colors.onSurfaceVariant, size = 16) }
            MetaTag("Serves ${r.servings}") { Glyph(R.drawable.ic_users, tint = colors.onSurfaceVariant, size = 16) }
            MetaTag(if (r.veg) "Veg" else "Non-veg") { VegMark(r.veg, described = false) }
            if (r.cuisine.isNotBlank()) MetaTag(r.cuisine) { Glyph(R.drawable.ic_globe, tint = colors.onSurfaceVariant, size = 16) }
        }
        if (r.allergens.isNotEmpty()) {
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "Contains ${r.allergens.joinToString(", ")}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(R.drawable.ic_warning, tint = n.warn, size = 18)
                Spacer(Modifier.width(6.dp))
                Text(r.allergens.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = n.warn)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MacroDonut(
                r.protein, r.carbs, r.fat,
                Modifier.size(112.dp),
                stroke = 12.dp,
                contentDescription = "${r.kcal} kcal a serving: ${r.protein} g protein, ${r.carbs} g carbs, ${r.fat} g fat",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.kcal}", style = MaterialTheme.typography.headlineSmall)
                    Text("kcal", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Legend(n.protein, "Protein", r.protein)
                Legend(n.carbs, "Carbs", r.carbs)
                Legend(n.fat, "Fat", r.fat)
                Legend(null, "Fiber", r.fiber)
            }
        }
        SheetHeading("Ingredients", "${checked.size}/${r.ingredients.size}")
        Column {
            r.ingredients.forEachIndexed { i, item ->
                val on = i in checked
                IngredientRow(item, on) { if (on) checked.remove(i) else checked.add(i) }
            }
        }
        SheetHeading("Steps")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            r.steps.forEachIndexed { i, step -> StepRow(i + 1, step) }
        }
        Column {
            if (r.source.startsWith("http")) {
                LinkRow(R.drawable.ic_link, Uri.parse(r.source).host?.removePrefix("www.") ?: "Recipe source", "Open recipe") { open(r.source) }
            }
            r.photoCredit?.let { credit ->
                LinkRow(R.drawable.ic_camera, credit, "Open photo licence", r.photoPage?.let { page -> { open(page) } })
            }
        }
    }
}

/** Opens [url] in a browser. False when nothing on the phone can open it, instead of crashing. */
private fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: ActivityNotFoundException) {
    false
}

@Composable
private fun MetaTag(text: String, leading: @Composable () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Donut legend: macro dot, name, grams. Fiber is not in the donut, so it gets no dot. */
@Composable
private fun Legend(color: Color?, label: String, grams: Int) {
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).then(if (color != null) Modifier.background(color, CircleShape) else Modifier))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.width(64.dp))
        Text("$grams g", style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun SheetHeading(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shopping and cooking checklist: the whole row toggles (role Checkbox), and done items are struck through. */
@Composable
private fun IngredientRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (checked) {
                Glyph(R.drawable.ic_check_circle_fill, tint = colors.primary, size = 22)
            } else {
                Box(Modifier.size(17.dp).border(1.5.dp, colors.outline, CircleShape))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) colors.onSurfaceVariant else colors.onSurface,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.semantics(mergeDescendants = true) {}) {
        Box(Modifier.size(28.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
}

/** A 48 dp credit line; a link (primary ink) when [onClick] is set, else plain text. */
@Composable
private fun LinkRow(@DrawableRes icon: Int, text: String, action: String, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = action, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, tint = colors.onSurfaceVariant, size = 18)
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (onClick != null) colors.primary else colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Body ----

private val Activity.art: Int
    @DrawableRes get() = when (this) {
        Activity.LOW -> R.drawable.emoji_sleeping
        Activity.LIGHT -> R.drawable.emoji_shoe
        Activity.MODERATE -> R.drawable.emoji_runner
        Activity.HIGH -> R.drawable.emoji_bolt
        Activity.ATHLETE -> R.drawable.emoji_trophy
    }

private val Goal.art: Int
    @DrawableRes get() = when (this) {
        Goal.LOSE -> R.drawable.emoji_fire
        Goal.MAINTAIN -> R.drawable.emoji_target
        Goal.GAIN -> R.drawable.emoji_biceps
    }

/** Body details for the daily targets: wrapping chip groups, checked number fields, and a live target above Save. */
@Composable
fun BodySheet(current: Body?, onSave: (Body) -> Unit, onDismiss: () -> Unit) {
    var sex by rememberSaveable { mutableStateOf(current?.sex ?: Sex.MALE) }
    var age by rememberSaveable { mutableStateOf(current?.age?.toString().orEmpty()) }
    var height by rememberSaveable { mutableStateOf(current?.heightCm?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(current?.weightKg?.toString().orEmpty()) }
    var activity by rememberSaveable { mutableStateOf(current?.activity ?: Activity.MODERATE) }
    var goal by rememberSaveable { mutableStateOf(current?.goal ?: Goal.MAINTAIN) }
    val body = bodyOrNull(sex, age, height, weight, activity, goal)
    val preview = remember(body) { body?.let(::targets) }

    NovaSheet(
        onDismiss,
        footer = {
            TargetPreview(preview)
            PrimaryButton("Save", onClick = { body?.let(onSave) }, enabled = body != null)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Body", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "Private, stays on this phone" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(R.drawable.ic_lock, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 16)
                Spacer(Modifier.width(4.dp))
                Text("Private", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ChoiceGroup("Sex") { Sex.entries.forEach { s -> Chip(s.label, sex == s, { sex = s }) } }
        NumberField("Age", age, { age = it }, "years", AGE_RANGE, icon = R.drawable.ic_calendar)
        NumberField("Height", height, { height = it }, "cm", HEIGHT_RANGE, icon = R.drawable.ic_ruler)
        NumberField("Weight", weight, { weight = it }, "kg", WEIGHT_RANGE, icon = R.drawable.ic_weight, last = true)
        ChoiceGroup("Activity", hint = activity.hint) {
            Activity.entries.forEach { a -> Chip(a.label, activity == a, { activity = a }, emoji = a.art) }
        }
        ChoiceGroup("Goal") { Goal.entries.forEach { g -> Chip(g.label, goal == g, { goal = g }, emoji = g.art) } }
    }
}

/** A labelled group of single-choice chips that wraps onto new rows instead of clipping its labels. */
@Composable
private fun ChoiceGroup(label: String, hint: String? = null, content: @Composable FlowRowScope.() -> Unit) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The target the typed numbers give, pinned above Save so it stays in view while typing. */
@Composable
private fun TargetPreview(t: Targets?) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Emoji(R.drawable.emoji_fire, 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            t?.kcal?.toString() ?: "–",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.alignByBaseline().then(if (t == null) Modifier.semantics { contentDescription = "No target yet" } else Modifier),
        )
        Text(
            " kcal/day",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        if (t != null) {
            Text(
                "P${t.protein} C${t.carbs} F${t.fat}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.alignByBaseline().semantics {
                    contentDescription = "${t.protein} g protein, ${t.carbs} g carbs, ${t.fat} g fat"
                },
            )
        }
    }
}

/**
 * Digits-only field with its range under it. An impossible value turns red as soon as it is typed, a value that is
 * just too short only once the field loses focus.
 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    unit: String,
    range: IntRange,
    modifier: Modifier = Modifier,
    digits: Int = 3,
    showRange: Boolean = true,
    @DrawableRes icon: Int? = null,
    last: Boolean = false,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val number = value.toIntOrNull()
    val error = value.isNotEmpty() && if (focused) !stillPossible(value, range, digits) else (number == null || number !in range)
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onChange(typed.filter { it in '0'..'9' }.take(digits)) },
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        textStyle = MaterialTheme.typography.labelLarge,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (icon != null) {
            { Icon(painterResource(icon), null, Modifier.size(20.dp)) }
        } else {
            null
        },
        suffix = { Text(unit, style = MaterialTheme.typography.labelLarge) },
        supportingText = if (showRange || error) {
            { Text("${range.first}–${range.last} $unit") }
        } else {
            null
        },
        isError = error,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
    )
}

// ---- Custom food ----

/** Log any food: name and kcal (up to 9999) are needed, macros are optional. The artwork follows the name. */
@Composable
internal fun AddFoodSheet(onAdd: (name: String, kcal: Int, protein: Int, carbs: Int, fat: Int) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    val energy = kcal.toIntOrNull()?.takeIf { it in KcalRange }
    val ready = name.isNotBlank() && energy != null
    val art = remember(name) { foodArt(name).res }

    NovaSheet(
        onDismiss,
        footer = {
            PrimaryButton(
                "Add",
                enabled = ready,
                onClick = {
                    if (energy != null && name.isNotBlank()) {
                        onAdd(name.trim(), energy, protein.toIntOrNull() ?: 0, carbs.toIntOrNull() ?: 0, fat.toIntOrNull() ?: 0)
                    }
                },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(art, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Text("Add food", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Calories", kcal, { kcal = it }, "kcal", KcalRange, Modifier.weight(1f), digits = 4, showRange = false)
            NumberField("Protein", protein, { protein = it }, "g", GramRange, Modifier.weight(1f), showRange = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Carbs", carbs, { carbs = it }, "g", GramRange, Modifier.weight(1f), showRange = false)
            NumberField("Fat", fat, { fat = it }, "g", GramRange, Modifier.weight(1f), showRange = false, last = true)
        }
    }
}
