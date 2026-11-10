package app.fitnova.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.Screen
import app.fitnova.Tab
import app.fitnova.WorkoutFlow

private data class TabItem(val tab: Tab, val label: String, val icon: Int)

private val TABS = listOf(
    TabItem(Tab.HOME, "Home", R.drawable.ic_home),
    TabItem(Tab.TRAIN, "Train", R.drawable.ic_train),
    TabItem(Tab.FOOD, "Food", R.drawable.ic_food),
    TabItem(Tab.SLEEP, "Sleep", R.drawable.ic_flame), // placeholder until the moon icon lands
    TabItem(Tab.RANKS, "Ranks", R.drawable.ic_ranks),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }
    // Outlives the tabs, so each tab keeps its scroll and filters while a page or a workout covers it.
    val tabState = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val flow = vm.flow
            val screen = vm.screen
            when {
                flow is WorkoutFlow.Live -> SessionScreen(flow, vm::finish)
                flow is WorkoutFlow.Done -> SummaryScreen(flow.workout, onSave = { vm.save(flow.workout) }, onDiscard = vm::discard)
                !vm.onboarded -> OnboardingScreen(vm)
                screen != null -> {
                    BackHandler(onBack = vm::closeScreen)
                    when (screen) {
                        Screen.PROFILE -> ProfileScreen(vm)
                        Screen.HISTORY -> HistoryScreen(vm)
                    }
                }
                else -> Tabs(vm, tabState)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 80.dp)) { data ->
            Snackbar(
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) { Text(data.visuals.message, style = MaterialTheme.typography.labelLarge) }
        }
    }

    // Pop-ups live here so any tab or page can open them.
    vm.setup?.let { ex -> SetupSheet(ex, vm.exercisePhotos[ex.id], onStart = { target, camera -> vm.start(ex, target, camera) }, onDismiss = { vm.setup = null }) }
    vm.recipe?.let { r -> RecipeSheet(r, onLog = { vm.logRecipe(r) }, onDismiss = { vm.recipe = null }) }
    if (vm.editingBody) BodySheet(vm.body, onSave = vm::saveBody, onDismiss = { vm.editingBody = false })
}

@Composable
private fun Tabs(vm: AppViewModel, state: SaveableStateHolder) {
    BackHandler(enabled = vm.tab != Tab.HOME) { vm.tab = Tab.HOME }
    Scaffold(
        containerColor = Color.Transparent, // AppRoot's Surface already paints the background
        bottomBar = { BottomBar(vm.tab) { vm.tab = it } },
    ) { padding ->
        Box {
            // Instant swap: a Crossfade composes and draws two whole screens through every transition.
            state.SaveableStateProvider(vm.tab) {
                when (vm.tab) {
                    Tab.HOME -> HomeScreen(vm, padding)
                    Tab.TRAIN -> TrainScreen(vm, padding)
                    Tab.FOOD -> FoodScreen(vm, padding)
                    Tab.SLEEP -> SleepScreen(vm, padding)
                    Tab.RANKS -> RanksScreen(vm, padding)
                }
            }
            // Solid strip behind the status bar so scrolled content never runs under the clock.
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
        }
    }
}

/** Floating-style bar: rounded top corners, hairline border, accent pill behind the active icon. */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 6.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { item ->
                val on = item.tab == selected
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(MaterialTheme.shapes.large)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(item.tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.clip(CircleShape).background(if (on) Tone.accent.copy(alpha = 0.14f) else Color.Transparent).padding(horizontal = 18.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(item.icon, tint = if (on) Tone.accent else colors.onSurfaceVariant, size = 22) }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (on) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
