package app.fitnova.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.domain.Category
import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode
import app.fitnova.domain.Photo

private val Featured = listOf(Exercise.PUSHUP, Exercise.SQUAT, Exercise.PLANK, Exercise.JACK, Exercise.LUNGE, Exercise.BURPEE)

@Composable
fun TrainScreen(vm: AppViewModel, padding: PaddingValues) {
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    val list = Exercise.entries.filter { category == null || it.category == category }
    val photos = vm.exercisePhotos
    val density = LocalDensity.current
    val featuredPx = with(density) { 280.dp.roundToPx() }
    val cardPx = with(density) { 180.dp.roundToPx() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 18.dp, bottom = padding.calculateBottomPadding() + 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Train", style = MaterialTheme.typography.headlineMedium) }
        if (category == null && Featured.any { photos[it.id] != null }) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(Featured.filter { photos[it.id] != null }, key = { it.id }) { ex ->
                        PhotoCard(
                            photos.getValue(ex.id).path, ex.title, "${ex.category.emoji} ${ex.defaultTarget} ${ex.unit}", featuredPx,
                            onClick = { vm.setup = ex },
                            modifier = Modifier.size(width = 280.dp, height = 170.dp),
                            badge = { if (ex.trackable) CameraBadge(Modifier.align(Alignment.TopEnd)) },
                        )
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("All", category == null, { category = null }) }
                items(Category.entries) { c -> Chip("${c.emoji} ${c.label}", category == c, { category = c }) }
            }
        }
        items(list, key = { it.id }) { ex -> ExerciseCard(ex, photos[ex.id], cardPx) { vm.setup = ex } }
    }
}

@Composable
private fun BoxScope.StickBadge(ex: Exercise) {
    Box(
        Modifier.align(Alignment.BottomStart).padding(8.dp).size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) { ExerciseIcon(ex, Modifier.size(26.dp)) }
}

@Composable
private fun CameraBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.padding(8.dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) { Glyph(R.drawable.ic_camera, size = 15, tint = Tone.accent) }
}

@Composable
private fun ExerciseCard(ex: Exercise, photo: Photo?, photoPx: Int, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = PaddingValues(8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.15f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AssetPhoto(photo.path, photoPx, Modifier.fillMaxSize(), ex.title)
                StickBadge(ex)
            } else {
                ExerciseIcon(ex, Modifier.fillMaxSize(0.82f))
            }
            if (ex.trackable) CameraBadge(Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(8.dp))
        Text(ex.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            "${ex.defaultTarget} ${ex.unit}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupSheet(exercise: Exercise, photo: Photo?, onStart: (target: Int, camera: Boolean) -> Unit, onDismiss: () -> Unit) {
    var target by remember(exercise) { mutableIntStateOf(exercise.defaultTarget) }
    var camera by remember(exercise) { mutableStateOf(exercise.trackable) }
    val hold = exercise.mode == Mode.HOLD
    val step = if (hold) 15 else if (target < 20) 1 else 5
    val context = LocalContext.current
    val heroPx = with(LocalDensity.current) { 360.dp.roundToPx() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        AssetPhoto(photo.path, heroPx, Modifier.fillMaxSize(), exercise.title)
                        Box(
                            Modifier.align(Alignment.BottomStart).padding(10.dp).size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center,
                        ) { ExerciseIcon(exercise, Modifier.size(40.dp)) }
                    } else {
                        ExerciseIcon(exercise, Modifier.size(170.dp))
                    }
                }
                photo?.credit?.let { credit ->
                    Text(
                        "📷 $credit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp).clickable(enabled = photo.page != null) {
                            photo.page?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(exercise.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag("${exercise.category.emoji} ${exercise.category.label}", Tone.accent)
                    exercise.muscles.split(" · ").forEach { Tag(it) }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    exercise.cues.forEach { Tag("✓ $it", Tone.good) }
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton(R.drawable.ic_minus) { target = (target - step).coerceAtLeast(step) }
                    Text(
                        if (hold) clock(target) else "$target reps",
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    StepButton(R.drawable.ic_plus) { target = (target + step).coerceAtMost(if (hold) 600 else 300) }
                }

                Spacer(Modifier.height(16.dp))
                Panel(padding = PaddingValues(14.dp)) {
                    if (exercise.trackable) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("📷 Camera check", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (camera) "${exercise.cameraTip} · full points" else "Manual · half points",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = camera,
                                onCheckedChange = { camera = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = Tone.accent, checkedThumbColor = Color.White),
                            )
                        }
                    } else {
                        Text("✋ Manual count · half points", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Start", onClick = { onStart(target, camera && exercise.trackable) })
        }
    }
}

@Composable
private fun StepButton(icon: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon) }
}
