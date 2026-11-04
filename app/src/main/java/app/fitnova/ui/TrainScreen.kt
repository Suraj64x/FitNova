package app.fitnova.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.domain.Category
import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode

@Composable
fun TrainScreen(vm: AppViewModel, padding: PaddingValues) {
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    val list = Exercise.entries.filter { category == null || it.category == category }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 18.dp, bottom = padding.calculateBottomPadding() + 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Train", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${Exercise.entries.size} exercises · ${Exercise.entries.count { it.trackable }} with camera form check",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("All", category == null, { category = null }) }
                items(Category.entries) { c -> Chip(c.label, category == c, { category = c }) }
            }
        }
        items(list, key = { it.id }) { ex -> ExerciseCard(ex) { vm.setup = ex } }
    }
}

@Composable
private fun ExerciseCard(ex: Exercise, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = PaddingValues(10.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.2f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            ExerciseIcon(ex, Modifier.fillMaxSize(0.82f))
            if (ex.trackable) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) { Glyph(R.drawable.ic_camera, size = 14, tint = Tone.accent) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(ex.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            "${ex.defaultTarget} ${ex.unit} · ${ex.category.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupSheet(exercise: Exercise, onStart: (target: Int, camera: Boolean) -> Unit, onDismiss: () -> Unit) {
    var target by remember(exercise) { mutableIntStateOf(exercise.defaultTarget) }
    var camera by remember(exercise) { mutableStateOf(exercise.trackable) }
    val hold = exercise.mode == Mode.HOLD
    val step = if (hold) 15 else if (target < 20) 1 else 5

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Box(
                Modifier.fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { ExerciseIcon(exercise, Modifier.size(170.dp)) }
            Spacer(Modifier.height(18.dp))
            Text(exercise.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(exercise.category.label, Tone.accent)
                exercise.muscles.split(" · ").forEach { Tag(it) }
            }

            Spacer(Modifier.height(22.dp))
            Text("Target", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(20.dp))
            Panel(padding = PaddingValues(16.dp)) {
                if (exercise.trackable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Camera form check", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (camera) "Counts reps and checks posture. Full points." else "Count it yourself. Half points.",
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
                    if (camera) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(R.drawable.ic_camera, size = 16, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(exercise.cameraTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Text("Counted by you", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "This move has no reliable camera signal yet, so you tap to count. Manual workouts earn half points.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Start workout", onClick = { onStart(target, camera && exercise.trackable) })
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
