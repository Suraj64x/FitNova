package app.fitnova.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.domain.Activity
import app.fitnova.domain.Body
import app.fitnova.domain.Goal
import app.fitnova.domain.Meal
import app.fitnova.domain.Recipe
import app.fitnova.domain.Sex
import app.fitnova.domain.Targets
import app.fitnova.domain.intake

private val MealTypes = listOf("breakfast", "lunch", "dinner", "snack")
private val MealEmoji = mapOf("breakfast" to "🍳", "lunch" to "🍛", "dinner" to "🍲", "snack" to "🥗")
private val WaterBlue = Color(0xFF38B2E8)
private val VegGreen = Color(0xFF1E9E55)
private val NonVegRed = Color(0xFFC6402F)

@Composable
fun FoodScreen(vm: AppViewModel, padding: PaddingValues) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    val targets = vm.targets
    var mealType by rememberSaveable { mutableStateOf<String?>(null) }
    var vegOnly by rememberSaveable { mutableStateOf(false) }
    var highProtein by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val recipes = vm.recipes.filter {
        (mealType == null || it.meal == mealType) && (!vegOnly || it.veg) && (!highProtein || it.protein >= 20)
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 18.dp, bottom = padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Nutrition", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            if (targets == null) {
                Panel {
                    EmptyNote("Daily targets", "Calories & protein, made for you", emoji = "🎯")
                    PrimaryButton("Set up", onClick = { vm.editingBody = true })
                }
            } else {
                TargetsCard(targets, meals, onEdit = { vm.editingBody = true })
            }
        }
        item { WaterCard(vm.water, onSet = { vm.addWater(it - vm.water) }) }
        item { SectionTitle("🍽️ Meals", action = "+ Add", onAction = { adding = true }) }
        item {
            if (meals.isEmpty()) {
                Panel { EmptyNote("Nothing logged", emoji = "🥣") }
            } else {
                val photos = vm.recipes.associate { it.id to it.photo }
                Panel(padding = PaddingValues(0.dp)) {
                    meals.forEachIndexed { i, m ->
                        MealRow(m, photo = m.recipeId?.let { photos[it] }, onRemove = { vm.removeMeal(m.id) })
                        if (i < meals.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        val featured = vm.recipes.filter { it.photo != null }.sortedByDescending { it.protein }.take(8)
        if (featured.isNotEmpty()) {
            item { SectionTitle("💪 Top protein") }
            item {
                val px = with(LocalDensity.current) { 240.dp.roundToPx() }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(featured, key = { it.id }) { r ->
                        PhotoCard(
                            r.photo!!, r.name, "${r.protein} g protein · ${r.kcal} kcal", px,
                            onClick = { vm.recipe = r },
                            modifier = Modifier.size(width = 240.dp, height = 160.dp),
                        )
                    }
                }
            }
        }
        item { SectionTitle("📖 Recipes") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("All", mealType == null, { mealType = null }) }
                items(MealTypes) { t -> Chip("${MealEmoji[t]} ${t.replaceFirstChar { it.uppercase() }}", mealType == t, { mealType = t }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("🌱 Veg", vegOnly, { vegOnly = !vegOnly })
                Chip("💪 Protein", highProtein, { highProtein = !highProtein })
            }
        }
        if (recipes.isEmpty()) {
            item { Panel { EmptyNote("No matches", emoji = "🔍") } }
        }
        items(recipes, key = { it.id }) { r -> RecipeCard(r) { vm.recipe = r } }
        item {
            Text(
                "Values are estimates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (adding) {
        AddFoodDialog(onAdd = { name, kcal, protein, carbs, fat -> adding = false; vm.logMeal(name, kcal, protein, carbs, fat) }, onDismiss = { adding = false })
    }
}

@Composable
private fun TargetsCard(t: Targets, meals: List<Meal>, onEdit: () -> Unit) {
    val eaten = meals.intake()
    val left = t.kcal - eaten.kcal
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(eaten.kcal.toFloat() / t.kcal, Modifier.size(132.dp), color = if (left < 0) Tone.warn else Tone.accent, stroke = 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${eaten.kcal}", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
                    Text("of ${t.kcal} kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroLine("Protein", eaten.protein, t.protein, Macro.protein)
                MacroLine("Carbs", eaten.carbs, t.carbs, Macro.carbs)
                MacroLine("Fat", eaten.fat, t.fat, Macro.fat)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (left >= 0) "$left kcal left today" else "${-left} kcal over today",
                style = MaterialTheme.typography.bodyMedium,
                color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else Tone.warn,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Edit targets",
                style = MaterialTheme.typography.labelLarge,
                color = Tone.accent,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MacroLine(label: String, value: Int, target: Int, color: Color) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$value / $target g", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(5.dp))
        Meter(value.toFloat() / target.coerceAtLeast(1), color, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MealRow(m: Meal, photo: String?, onRemove: () -> Unit) {
    val thumbPx = with(LocalDensity.current) { 46.dp.roundToPx() }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (photo != null) {
            AssetPhoto(photo, thumbPx, Modifier.size(46.dp).clip(MaterialTheme.shapes.small), m.name)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${m.protein} g protein · ${m.carbs} g carbs · ${m.fat} g fat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${m.kcal} kcal", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Glyph(R.drawable.ic_close, size = 16, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VegMark(veg: Boolean) {
    // Indian-style food mark: green square-dot for veg, red for non-veg.
    val c = if (veg) VegGreen else NonVegRed
    Box(Modifier.size(16.dp).clip(MaterialTheme.shapes.extraSmall).background(c.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
    }
}

@Composable
private fun RecipeCard(r: Recipe, onClick: () -> Unit) {
    val thumbPx = with(LocalDensity.current) { 92.dp.roundToPx() }
    Panel(onClick = onClick, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.photo != null) {
                AssetPhoto(r.photo, thumbPx, Modifier.size(92.dp).clip(MaterialTheme.shapes.medium), r.name)
            } else {
                MacroDonut(r.protein, r.carbs, r.fat, Modifier.size(58.dp), stroke = 6.dp) {
                    Text("${r.kcal}", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VegMark(r.veg)
                    Spacer(Modifier.width(8.dp))
                    Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${r.meal.replaceFirstChar { it.uppercase() }} · ${r.minutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag("${r.kcal} kcal")
                    Tag("${r.protein} g protein", Macro.protein)
                }
            }
            Glyph(R.drawable.ic_chevron, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeSheet(r: Recipe, onLog: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // Scrollable body with the "Log" button pinned below it, so it's always one tap away.
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (r.photo != null) {
                val heroPx = with(LocalDensity.current) { 360.dp.roundToPx() }
                AssetPhoto(r.photo, heroPx, Modifier.fillMaxWidth().height(210.dp).clip(MaterialTheme.shapes.large), r.name)
                r.photoCredit?.let { credit ->
                    Text(
                        "📷 $credit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable(enabled = r.photoPage != null) {
                                r.photoPage?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                            },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                VegMark(r.veg)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${r.meal.replaceFirstChar { it.uppercase() }} · ${r.cuisine} · ${r.minutes} min · serves ${r.servings}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(r.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                MacroDonut(r.protein, r.carbs, r.fat, Modifier.size(112.dp), stroke = 10.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${r.kcal}", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
                        Text("kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Legend("Protein", "${r.protein} g", Macro.protein)
                    Legend("Carbs", "${r.carbs} g", Macro.carbs)
                    Legend("Fat", "${r.fat} g", Macro.fat)
                    Legend("Fiber", "${r.fiber} g", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (r.tags.isNotEmpty() || r.allergens.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.tags.forEach { Tag(it.replace('-', ' '), Tone.accent) }
                    r.allergens.forEach { Tag("Contains $it", Tone.warn) }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Ingredients", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            r.ingredients.forEach { item ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(Modifier.width(10.dp))
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Steps", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            r.steps.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium) }
                    Spacer(Modifier.width(12.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (r.source.startsWith("http")) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🔗 ${Uri.parse(r.source).host?.removePrefix("www.") ?: "Source"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Tone.accent,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.source))) }
                        .padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Log 1 serving · ${r.kcal} kcal", onClick = onLog)
        }
    }
}

@Composable
private fun Legend(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodySheet(current: Body?, onSave: (Body) -> Unit, onDismiss: () -> Unit) {
    var sex by remember { mutableStateOf(current?.sex ?: Sex.MALE) }
    var age by remember { mutableStateOf(current?.age?.toString() ?: "") }
    var height by remember { mutableStateOf(current?.heightCm?.toString() ?: "") }
    var weight by remember { mutableStateOf(current?.weightKg?.toString() ?: "") }
    var activity by remember { mutableStateOf(current?.activity ?: Activity.MODERATE) }
    var goal by remember { mutableStateOf(current?.goal ?: Goal.MAINTAIN) }
    val a = age.toIntOrNull()
    val h = height.toIntOrNull()
    val w = weight.toIntOrNull()
    val valid = a in 13..100 && h in 120..230 && w in 30..250

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Your body & goal", style = MaterialTheme.typography.headlineSmall)
            Text("Stays on this phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Segmented(Sex.entries.map { it.label }, Sex.entries.indexOf(sex), { sex = Sex.entries[it] })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Age", age, { age = it }, "years")
                NumberField("Height", height, { height = it }, "cm")
                NumberField("Weight", weight, { weight = it }, "kg")
            }
            Text("Activity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Segmented(Activity.entries.map { it.label }, Activity.entries.indexOf(activity), { activity = Activity.entries[it] })
            Text("Goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Segmented(Goal.entries.map { it.label }, Goal.entries.indexOf(goal), { goal = Goal.entries[it] })
            PrimaryButton("Save targets", enabled = valid, onClick = { onSave(Body(sex, a!!, h!!, w!!, activity, goal)) })
        }
    }
}

@Composable
private fun RowScope.NumberField(label: String, value: String, onChange: (String) -> Unit, suffix: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(3)) },
        label = { Text(label) },
        suffix = { Text(suffix, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun AddFoodDialog(onAdd: (String, Int, Int, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val k = kcal.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Add food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Calories", kcal, { kcal = it }, "kcal")
                    NumberField("Protein", protein, { protein = it }, "g")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Carbs", carbs, { carbs = it }, "g")
                    NumberField("Fat", fat, { fat = it }, "g")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && k != null,
                onClick = { onAdd(name.trim(), k!!, protein.toIntOrNull() ?: 0, carbs.toIntOrNull() ?: 0, fat.toIntOrNull() ?: 0) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Eight glasses: tap one to set the count (tap the last filled one to undo it). */
@Composable
private fun WaterCard(glasses: Int, onSet: (Int) -> Unit) {
    Panel(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💧 Water", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$glasses / 8", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = WaterBlue)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..8) {
                val filled = i <= glasses
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (filled) WaterBlue else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSet(if (i == glasses) i - 1 else i) },
                )
            }
        }
    }
}
