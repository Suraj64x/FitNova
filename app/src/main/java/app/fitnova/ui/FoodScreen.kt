package app.fitnova.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.domain.FoodArt
import app.fitnova.domain.GLASS_ML
import app.fitnova.domain.HIGH_PROTEIN_G
import app.fitnova.domain.Intake
import app.fitnova.domain.Meal
import app.fitnova.domain.Recipe
import app.fitnova.domain.Targets
import app.fitnova.domain.WATER_GOAL
import app.fitnova.domain.WATER_MAX
import app.fitnova.domain.filterRecipes
import app.fitnova.domain.foodArt
import app.fitnova.domain.intake
import app.fitnova.domain.tapGlass
import app.fitnova.domain.targets
import kotlinx.coroutines.launch

private val MealTypes = listOf("breakfast", "lunch", "dinner", "snack")
private val RecipePhoto = 72.dp

/** Chip artwork for a meal type: the time of day, and a banana for snacks. */
@DrawableRes
private fun mealTypeArt(type: String) = when (type) {
    "breakfast" -> R.drawable.emoji_sunrise
    "lunch" -> R.drawable.emoji_sun
    "dinner" -> R.drawable.emoji_moon
    else -> R.drawable.emoji_banana
}

/** Fluent artwork for a food picture. */
internal val FoodArt.res: Int
    @DrawableRes get() = when (this) {
        FoodArt.EGG -> R.drawable.emoji_egg
        FoodArt.CHICKEN -> R.drawable.emoji_chicken
        FoodArt.BANANA -> R.drawable.emoji_banana
        FoodArt.AVOCADO -> R.drawable.emoji_avocado
        FoodArt.APPLE -> R.drawable.emoji_apple
        FoodArt.FLATBREAD -> R.drawable.emoji_flatbread
        FoodArt.RICE -> R.drawable.emoji_rice
        FoodArt.BOWL -> R.drawable.emoji_bowl
        FoodArt.MILK -> R.drawable.emoji_milk
        FoodArt.SALAD -> R.drawable.emoji_salad
        FoodArt.CURRY -> R.drawable.emoji_curry
        FoodArt.BROCCOLI -> R.drawable.emoji_broccoli
        FoodArt.PLATE -> R.drawable.emoji_plate
    }

@Composable
fun FoodScreen(vm: AppViewModel, padding: PaddingValues) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    val body = vm.body
    val goal = remember(body) { body?.let(::targets) }
    val eaten = remember(meals) { meals.intake() }
    // Saveable, so the search and chips survive tab switches (AppRoot keeps each tab's saved state).
    var query by rememberSaveable { mutableStateOf("") }
    var mealType by rememberSaveable { mutableStateOf<String?>(null) }
    var vegOnly by rememberSaveable { mutableStateOf(false) }
    var highProtein by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val recipes = vm.recipes
    val shown = remember(recipes, query, mealType, vegOnly, highProtein) { filterRecipes(recipes, query, mealType, vegOnly, highProtein) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gutter = if (LocalWindowInfo.current.containerDpSize.width >= 400.dp) 20.dp else 16.dp
    val photoPx = with(LocalDensity.current) { RecipePhoto.roundToPx() }

    // Food keeps its own snackbar: the app-wide one has no action button, and Undo needs one.
    fun show(text: String, undo: Meal? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(text, actionLabel = undo?.let { "Undo" }, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && undo != null) vm.restoreMeal(undo)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            // Edge-to-edge: without this the keyboard would cover the search field instead of the list scrolling up.
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header", contentType = "header") {
                ScreenHeader("Food") { ProfileButton(vm.username, vm::openProfile) }
            }
            item(key = "targets", contentType = "targets") {
                if (goal == null) {
                    HeroPanel {
                        EmptyState(
                            "Daily targets",
                            "Calories and macros for your goal",
                            emoji = R.drawable.emoji_salad,
                            action = "Set targets",
                            onAction = { vm.editingBody = true },
                        )
                    }
                } else {
                    TargetsCard(goal, eaten, onEdit = { vm.editingBody = true }, onInfo = { show("Estimates, not medical advice") })
                }
            }
            item(key = "water", contentType = "water") { WaterCard(vm.water, vm::addWater) }
            item(key = "meals", contentType = "title") {
                SectionTitle("Meals", R.drawable.emoji_plate, action = "Add", onAction = { adding = true })
            }
            if (meals.isEmpty()) {
                item(key = "no-meals", contentType = "empty") { NoMeals(onAdd = { adding = true }) }
            }
            items(meals, key = { "meal:${it.id}" }, contentType = { "meal" }) { m ->
                MealRow(m, onRemove = { vm.removeMeal(m.id); show("Meal removed", undo = m) }, Modifier.animateItem())
            }
            item(key = "recipes", contentType = "title") { SectionTitle("Recipes", R.drawable.emoji_curry) }
            item(key = "search", contentType = "search") { SearchField(query) { query = it } }
            item(key = "filters", contentType = "filters") {
                LazyRow(
                    Modifier.bleed(gutter),
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all", contentType = "chip") { Chip("All", mealType == null, { mealType = null }) }
                    items(MealTypes, key = { it }, contentType = { "chip" }) { t ->
                        Chip(
                            t.replaceFirstChar { it.titlecase() },
                            mealType == t,
                            { mealType = if (mealType == t) null else t },
                            emoji = mealTypeArt(t),
                        )
                    }
                    item(key = "veg", contentType = "chip") {
                        Chip("Veg", vegOnly, { vegOnly = !vegOnly }, emoji = R.drawable.emoji_seedling)
                    }
                    item(key = "protein", contentType = "chip") {
                        Chip("${HIGH_PROTEIN_G}g+ protein", highProtein, { highProtein = !highProtein }, emoji = R.drawable.emoji_biceps)
                    }
                }
            }
            // Recipes load just after launch: "No matches" only once there is something to match.
            if (recipes.isNotEmpty() && shown.isEmpty()) {
                item(key = "no-match", contentType = "empty") {
                    Panel {
                        EmptyState(
                            "No matches",
                            emoji = R.drawable.emoji_bowl,
                            action = "Clear filters",
                            onAction = { query = ""; mealType = null; vegOnly = false; highProtein = false },
                        )
                    }
                }
            }
            items(shown, key = { "recipe:${it.id}" }, contentType = { "recipe" }) { r ->
                RecipeCard(r, photoPx) { vm.recipe = r }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(start = gutter, end = gutter, bottom = padding.calculateBottomPadding() + 12.dp),
        ) { FoodSnackbar(it) }
    }

    if (adding) {
        AddFoodSheet(
            onAdd = { name, kcal, protein, carbs, fat ->
                adding = false
                vm.logMeal(name, kcal, protein, carbs, fat)
            },
            onDismiss = { adding = false },
        )
    }
}

/** Lets a horizontal list run edge to edge through the page gutter while its first item still lines up with the page. */
private fun Modifier.bleed(gutter: Dp) = layout { measurable, constraints ->
    val extra = (gutter * 2).roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

// ---- Targets ----

/** kcal left (or over, in the warn ink with an icon) on an open arc, eaten and goal beside it, then the macros. */
@Composable
private fun TargetsCard(t: Targets, eaten: Intake, onEdit: () -> Unit, onInfo: () -> Unit) {
    val n = Nova.colors
    val colors = MaterialTheme.colorScheme
    val left = t.kcal - eaten.kcal
    val over = left < 0
    HeroPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Emoji(R.drawable.emoji_fire, 24.dp)
            Spacer(Modifier.width(8.dp))
            Text("Today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            IconCircleButton(R.drawable.ic_info, "About these numbers", onInfo, container = Color.Transparent, tint = colors.onSurfaceVariant)
            IconCircleButton(R.drawable.ic_edit, "Edit targets", onEdit, container = Color.Transparent, tint = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arc(
                progress = eaten.kcal.toFloat() / t.kcal,
                color = if (over) n.warn else n.points,
                size = 128.dp,
                stroke = 16.dp,
                contentDescription = if (over) "${-left} kcal over your ${t.kcal} kcal goal" else "$left of ${t.kcal} kcal left",
            ) {
                // The number shrinks to fit inside the arc ("+1250" at 320 dp, or with large fonts).
                Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (over) "+${-left}" else "$left",
                        color = if (over) n.warn else colors.onSurface,
                        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 36.sp),
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (over) {
                            Glyph(R.drawable.ic_warning, tint = n.warn, size = 14)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            if (over) "over" else "left",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (over) n.warn else colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KcalStat("Eaten", eaten.kcal)
                KcalStat("Goal", t.kcal, unit = "kcal")
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroRow("Protein", eaten.protein, t.protein, n.protein)
            MacroRow("Carbs", eaten.carbs, t.carbs, n.carbs)
            MacroRow("Fat", eaten.fat, t.fat, n.fat)
        }
    }
}

@Composable
private fun KcalStat(label: String, value: Int, unit: String? = null) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row {
            Text("$value", style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.alignByBaseline())
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

/** Full-width macro line ("Protein · 45 of 120 g") over its meter, so the numbers never wrap at 320 dp. */
@Composable
private fun MacroRow(label: String, value: Int, target: Int, color: Color) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.weight(1f).alignByBaseline())
            Text("$value", style = MaterialTheme.typography.labelLarge, modifier = Modifier.alignByBaseline())
            Text(
                " of $target g",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Meter(value.toFloat() / target.coerceAtLeast(1), color, Modifier.fillMaxWidth())
    }
}

// ---- Water ----

/** Eight glasses plus −/+. Above eight the glasses stay full, a "+N" shows the rest and only −/+ change the count. */
@Composable
private fun WaterCard(glasses: Int, onChange: (Int) -> Unit) {
    val n = Nova.colors
    val colors = MaterialTheme.colorScheme
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(R.drawable.emoji_droplet, tint = n.water, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Water", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${glasses * GLASS_ML} ml", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
            IconCircleButton(R.drawable.ic_minus, "Remove a glass", onClick = { onChange(-1) }, enabled = glasses > 0)
            Row(
                Modifier.widthIn(min = 48.dp).clearAndSetSemantics {
                    contentDescription = "$glasses of $WATER_GOAL glasses"
                    liveRegion = LiveRegionMode.Polite
                },
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("$glasses", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.alignByBaseline())
                Text("/$WATER_GOAL", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.alignByBaseline())
            }
            IconCircleButton(
                R.drawable.ic_plus,
                "Add a glass of water",
                onClick = { onChange(1) },
                container = n.water.copy(alpha = 0.14f),
                enabled = glasses < WATER_MAX,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..WATER_GOAL) {
                Glass(
                    index = i,
                    filled = i <= glasses,
                    enabled = glasses <= WATER_GOAL,
                    onClick = { onChange(tapGlass(glasses, i) - glasses) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (glasses > WATER_GOAL) Tag("+${glasses - WATER_GOAL}", tone = n.water, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A drawn glass that fills from the bottom; the fill animates in the draw phase. */
@Composable
private fun Glass(index: Int, filled: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val water = Nova.colors.water
    val edge = MaterialTheme.colorScheme.outline
    val level = animateFloatAsState(if (filled) 1f else 0f, tween(200), label = "glass")
    Spacer(
        modifier
            .height(48.dp)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 22.dp),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "Glass $index of $WATER_GOAL, $GLASS_ML ml"
                stateDescription = if (filled) "Full" else "Empty"
            }
            .drawWithCache {
                val w = minOf(size.width - 6.dp.toPx(), 22.dp.toPx())
                val h = w * 1.36f
                val x = (size.width - w) / 2f
                val y = (size.height - h) / 2f
                val taper = w * 0.14f
                val glass = Path().apply {
                    moveTo(x, y)
                    lineTo(x + w, y)
                    lineTo(x + w - taper, y + h)
                    lineTo(x + taper, y + h)
                    close()
                }
                val rim = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round)
                val bottom = y + h
                val fullDepth = h * 0.8f
                onDrawBehind {
                    val l = level.value
                    if (l > 0f) clipRect(top = bottom - fullDepth * l) { drawPath(glass, water) }
                    drawPath(glass, if (l > 0.5f) water else edge, style = rim)
                }
            },
    )
}

// ---- Meals ----

@Composable
private fun MealRow(m: Meal, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val art = remember(m.name) { foodArt(m.name).res }
    Panel(modifier, padding = PaddingValues(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(art, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.SemiBold)) { append("${m.kcal} kcal") }
                            append(" · P${m.protein} C${m.carbs} F${m.fat}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics {
                            contentDescription = "${m.kcal} kcal, ${m.protein} g protein, ${m.carbs} g carbs, ${m.fat} g fat"
                        },
                    )
                }
            }
            IconCircleButton(R.drawable.ic_trash, "Remove ${m.name}", onRemove, container = Color.Transparent, tint = colors.onSurfaceVariant)
        }
    }
}

/** Empty meal log: a salad between an avocado and an apple, and one line. Tapping it adds a food. */
@Composable
private fun NoMeals(onAdd: () -> Unit) {
    Panel(onClick = onAdd) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 140.dp, height = 72.dp)) {
                EmojiTile(R.drawable.emoji_avocado, size = 52.dp, modifier = Modifier.align(Alignment.BottomStart).rotate(-10f))
                EmojiTile(R.drawable.emoji_apple, size = 52.dp, modifier = Modifier.align(Alignment.BottomEnd).rotate(10f))
                EmojiTile(R.drawable.emoji_salad, size = 68.dp, modifier = Modifier.align(Alignment.TopCenter))
            }
            Spacer(Modifier.height(12.dp))
            Text("No meals yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Recipes ----

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = { onChange(it.take(40)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search recipes", maxLines = 1) },
        leadingIcon = { Glyph(R.drawable.ic_search, tint = colors.onSurfaceVariant) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            { IconCircleButton(R.drawable.ic_close, "Clear search", { onChange("") }, container = Color.Transparent, tint = colors.onSurfaceVariant) }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

/** Photo, name, veg mark, meal and time, then kcal and protein tags that wrap rather than clip on narrow phones. */
@Composable
private fun RecipeCard(r: Recipe, photoPx: Int, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val photo = r.photo
            if (photo != null) {
                AssetPhoto(photo, photoPx, Modifier.size(RecipePhoto).clip(MaterialTheme.shapes.medium))
            } else {
                EmojiTile(remember(r.name) { foodArt(r.name).res }, size = RecipePhoto)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VegMark(r.veg)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${r.meal.replaceFirstChar { it.titlecase() }} · ${r.minutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tag("${r.kcal} kcal")
                    Tag("${r.protein}g protein", tone = Nova.colors.protein)
                }
            }
        }
    }
}

/**
 * Indian food mark: a square with a dot (veg, green) or a triangle (non-veg, red), so it never relies on colour
 * alone. TalkBack reads it unless [described] is false (when a word next to it already says it).
 */
@Composable
internal fun VegMark(veg: Boolean, described: Boolean = true, modifier: Modifier = Modifier) {
    val color = if (veg) Nova.colors.good else Nova.colors.bad
    Spacer(
        modifier
            .size(16.dp)
            .then(if (described) Modifier.semantics { contentDescription = if (veg) "Vegetarian" else "Non-vegetarian" } else Modifier)
            .drawWithCache {
                val rim = Stroke(1.5.dp.toPx())
                val w = size.width
                val h = size.height
                val corner = CornerRadius(3.dp.toPx())
                val mark = Path().apply {
                    if (veg) {
                        addOval(Rect(Offset(w / 2f, h / 2f), w * 0.22f))
                    } else {
                        moveTo(w / 2f, h * 0.27f)
                        lineTo(w * 0.76f, h * 0.72f)
                        lineTo(w * 0.24f, h * 0.72f)
                        close()
                    }
                }
                val inset = rim.width / 2f
                onDrawBehind {
                    drawRoundRect(color, Offset(inset, inset), Size(w - rim.width, h - rim.width), corner, style = rim)
                    drawPath(mark, color)
                }
            },
    )
}

@Composable
private fun FoodSnackbar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    val label = data.visuals.actionLabel
    Snackbar(
        action = if (label == null) {
            null
        } else {
            { TextButton(onClick = data::performAction) { Text(label, style = MaterialTheme.typography.labelLarge, color = colors.inversePrimary) } }
        },
        shape = MaterialTheme.shapes.medium,
        containerColor = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
    ) { Text(data.visuals.message, style = MaterialTheme.typography.labelLarge) }
}
