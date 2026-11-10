package app.fitnova.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.domain.Category
import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode
import app.fitnova.domain.Photo
import app.fitnova.domain.ROUTINES
import app.fitnova.domain.Routine

/** "20 reps" or "0:45". */
internal fun Exercise.targetLabel(target: Int) = if (mode == Mode.HOLD) clock(target) else "$target reps"

@Composable
fun TrainScreen(vm: AppViewModel, padding: PaddingValues) {
    // Saveable, so the filter and an open routine survive tab switches and rotation.
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    val moves = remember(category) { Exercise.entries.filter { category == null || it.category == category } }
    val photos = vm.exercisePhotos
    val gutter = if (LocalConfiguration.current.screenWidthDp >= 400) 20.dp else 16.dp
    val photoPx = with(LocalDensity.current) { 200.dp.roundToPx() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(136.dp),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            ScreenHeader("Train", trailing = { ProfileButton(vm.username, vm::openProfile) })
        }
        item(key = "routines_title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") { SectionTitle("Routines") }
        item(key = "routines", span = { GridItemSpan(maxLineSpan) }, contentType = "routines") {
            LazyRow(
                Modifier.bleed(gutter),
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ROUTINES, key = { it.id }, contentType = { "routine" }) { r -> RoutineCard(r, onClick = { preview = r.id }) }
            }
        }
        item(key = "moves_title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") { SectionTitle("Moves") }
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }, contentType = "chips") {
            LazyRow(
                Modifier.bleed(gutter).selectableGroup(),
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") { CategoryChip(null, category == null) { category = null } }
                items(Category.entries, key = { it.name }) { c -> CategoryChip(c, category == c) { category = c } }
            }
        }
        items(moves, key = { it.id }, contentType = { "move" }) { ex ->
            MoveCard(ex, photos[ex.id], vm.lastTarget(ex), photoPx) { vm.setup = ex }
        }
    }

    preview?.let(Routine::of)?.let { r ->
        RoutineSheet(
            r,
            vm,
            onStart = { camera ->
                preview = null
                vm.startRoutine(r, camera)
            },
            onDismiss = { preview = null },
        )
    }
}

/** Lets a row inside the padded grid scroll edge to edge; give it the same [gutter] as content padding. */
private fun Modifier.bleed(gutter: Dp) = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Filter chip in the category's own colour (14 %); [category] null is "All". Selected = inverse, like [Chip]. */
@Composable
private fun CategoryChip(category: Category?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val idle = category?.let { Nova.colors.of(it).copy(alpha = 0.14f).compositeOver(colors.surface) } ?: colors.surfaceVariant
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) colors.inverseSurface else idle)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (category != null) {
            Emoji(category.art, 20.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            category?.label ?: "All",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.inverseOnSurface else colors.onSurface,
            maxLines = 1,
        )
    }
}

/** Exercise card tinted with its category colour: photo, camera badge, a two-line title and the target. */
@Composable
private fun MoveCard(ex: Exercise, photo: Photo?, target: Int, photoPx: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = Nova.colors.of(ex.category)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(tint.copy(alpha = 0.12f).compositeOver(colors.surfaceContainer))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(MaterialTheme.shapes.medium)
                .background(tint.copy(alpha = 0.14f)),
        ) {
            // Same slot before and after the photos load, so nothing jumps.
            if (photo != null) {
                AssetPhoto(photo.path, photoPx, Modifier.matchParentSize(), targetHeightPx = (photoPx / 1.25f).toInt())
            } else {
                ExerciseIcon(ex, Modifier.align(Alignment.Center).fillMaxSize(0.7f))
            }
            if (ex.trackable) CameraBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
            Text(ex.title, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Emoji(ex.category.art, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(ex.targetLabel(target), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Solid chip over the photo: this move is counted and checked by the camera. */
@Composable
private fun CameraBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.background(colors.surfaceContainer, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(R.drawable.ic_camera, size = 14, tint = colors.primary)
        Spacer(Modifier.width(4.dp))
        Text("Camera", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
    }
}

/** Starts CameraX while a sheet is open, so the first preview comes up sooner. */
@Composable
internal fun WarmCamera(on: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(on) { if (on) ProcessCameraProvider.getInstance(context.applicationContext) }
}

private fun Context.openPage(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } // no browser: nothing to do
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupSheet(
    exercise: Exercise,
    photo: Photo?,
    onStart: (target: Int, camera: Boolean) -> Unit,
    onDismiss: () -> Unit,
    vm: AppViewModel = viewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val hold = exercise.mode == Mode.HOLD
    var target by rememberSaveable(exercise) { mutableIntStateOf(vm.lastTarget(exercise)) }
    var camera by rememberSaveable(exercise) { mutableStateOf(exercise.trackable) }
    var credit by rememberSaveable(exercise) { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val heroPx = with(density) { 360.dp.roundToPx() }
    val heroHeightPx = with(density) { 200.dp.roundToPx() }
    WarmCamera(camera && exercise.trackable)

    NovaSheet(onDismiss, footer = { PrimaryButton("Start", onClick = { onStart(target, camera && exercise.trackable) }) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.large)
                .background(Nova.colors.of(exercise.category).copy(alpha = 0.14f)),
        ) {
            if (photo != null) {
                AssetPhoto(photo.path, heroPx, Modifier.matchParentSize(), targetHeightPx = heroHeightPx)
            } else {
                ExerciseIcon(exercise, Modifier.align(Alignment.Center).size(150.dp))
            }
            if (photo?.credit != null) {
                IconCircleButton(
                    R.drawable.ic_info, "Photo credit", { credit = !credit },
                    container = colors.surfaceContainer,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
        val page = photo?.page
        if (credit && photo?.credit != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = page != null) { page?.let(context::openPage) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(photo.credit, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (page != null) Glyph(R.drawable.ic_arrow_up_right, size = 16, tint = colors.onSurfaceVariant)
            }
        }
        Column {
            Text(exercise.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Emoji(exercise.category.art, 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(exercise.muscles, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercise.cues.forEach { CueChip(it) }
        }
        Stepper(
            value = target,
            onChange = { target = it },
            // Up from 19 goes to 20, down from 20 to 19: the step size is taken on the side being moved into.
            step = if (hold) HoldStep else RepStep,
            range = if (hold) 5..600 else 1..300,
            unit = if (hold) null else "reps",
            format = if (hold) ::clock else Int::toString,
        )
        Panel(padding = PaddingValues(vertical = 4.dp)) {
            if (exercise.trackable) {
                ToggleRow(
                    R.drawable.ic_camera_duo, "Camera",
                    subtitle = if (camera) exercise.cameraTip else "Off · half points",
                    checked = camera,
                    onCheckedChange = { camera = it },
                )
            } else {
                ListRow("Count by hand", "Half points", leading = { IconTile(R.drawable.ic_hand_wave_duo, colors.onSurfaceVariant) })
            }
            ToggleRow(R.drawable.ic_speaker_duo, "Voice coach", checked = vm.voice, onCheckedChange = vm::changeVoice)
        }
    }
}

private val RepStep: (Int) -> Int = { if (it < 20) 1 else 5 }
private val HoldStep: (Int) -> Int = { if (it < 60) 5 else 15 }

@Composable
private fun CueChip(text: String) {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(R.drawable.ic_check, size = 16, tint = Nova.colors.good)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
