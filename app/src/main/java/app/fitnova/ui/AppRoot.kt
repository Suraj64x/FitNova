package app.fitnova.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.fitnova.AppViewModel
import app.fitnova.R
import app.fitnova.Screen
import app.fitnova.Tab
import app.fitnova.WorkoutFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

private class TabItem(val tab: Tab, val label: String, @param:DrawableRes val icon: Int, @param:DrawableRes val active: Int)

private val TABS = listOf(
    TabItem(Tab.HOME, "Home", R.drawable.ic_home, R.drawable.ic_home_fill),
    TabItem(Tab.TRAIN, "Train", R.drawable.ic_train, R.drawable.ic_train_fill),
    TabItem(Tab.FOOD, "Food", R.drawable.ic_food, R.drawable.ic_food_fill),
    TabItem(Tab.SLEEP, "Sleep", R.drawable.ic_moon, R.drawable.ic_moon_fill),
    TabItem(Tab.RANKS, "Ranks", R.drawable.ic_ranks, R.drawable.ic_ranks_fill),
)

/** A message stays up at least this long before a newer one replaces it. */
private const val MIN_SHOWN_MS = 2_000L

@Composable
fun AppRoot(vm: AppViewModel) {
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        val queue = Channel<String>(Channel.UNLIMITED)
        launch { vm.messages.collect { queue.send(it) } }
        showPaced(queue, MIN_SHOWN_MS) { snackbar.showSnackbar(it) }
    }
    // Outlives the tabs, so each tab keeps its scroll and filters while a page or a workout covers it.
    val tabState = rememberSaveableStateHolder()
    val flow = vm.flow
    val screen = vm.screen

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                flow != null -> Enter(flow::class, slide = true) { FlowScreen(vm, flow) } // its screens handle back
                !vm.onboarded -> {
                    BackHandler { vm.finishOnboarding(null) } // back skips; pages inside can still step back first
                    Enter("onboarding", slide = true) { OnboardingScreen(vm) }
                }
                screen != null -> {
                    BackHandler(onBack = vm::closeScreen)
                    Enter(screen, slide = true) {
                        when (screen) {
                            Screen.PROFILE -> ProfileScreen(vm)
                            Screen.HISTORY -> HistoryScreen(vm)
                        }
                    }
                }
                else -> Tabs(vm, tabState, snackbar)
            }
        }
        // The tabs host their messages above the bar; full-screen pages at the bottom, workouts at the top (their controls sit low).
        when {
            flow != null -> FitNovaTheme(dark = true) {
                MessageHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp))
            }
            screen != null || !vm.onboarded -> MessageHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        }
    }

    // Pop-ups live here so any tab or page can open them.
    vm.setup?.let { ex -> SetupSheet(ex, vm.exercisePhotos[ex.id], onStart = { target, camera -> vm.start(ex, target, camera) }, onDismiss = { vm.setup = null }) }
    vm.recipe?.let { r -> RecipeSheet(r, onLog = { vm.logRecipe(r) }, onDismiss = { vm.recipe = null }) }
    if (vm.editingBody) BodySheet(vm.body, onSave = vm::saveBody, onDismiss = { vm.editingBody = false })
}

/** Workout mode. A new [WorkoutFlow] step needs its line here (the `when` is exhaustive, so the build says so). */
@Composable
private fun FlowScreen(vm: AppViewModel, flow: WorkoutFlow) {
    when (flow) {
        is WorkoutFlow.Live -> SessionScreen(flow, vm::finish)
        is WorkoutFlow.Done -> SummaryScreen(flow.workout, onSave = { vm.save(flow.workout) }, onDiscard = vm::discard)
    }
}

@Composable
private fun Tabs(vm: AppViewModel, state: SaveableStateHolder, snackbar: SnackbarHostState) {
    val tab = vm.tab
    BackHandler(enabled = tab != Tab.HOME) { vm.tab = Tab.HOME }
    Scaffold(
        containerColor = Color.Transparent, // AppRoot's Surface already paints the background
        bottomBar = { BottomBar(tab) { vm.tab = it } },
        // The Scaffold stacks the host on top of the bar; 16 dp of air between them.
        snackbarHost = { MessageHost(snackbar, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Instant swap plus a short fade of the incoming tab only (a Crossfade would draw two whole screens).
            Enter(tab, slide = false) {
                state.SaveableStateProvider(tab) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(vm, padding)
                        Tab.TRAIN -> TrainScreen(vm, padding)
                        Tab.FOOD -> FoodScreen(vm, padding, snackbar)
                        Tab.SLEEP -> SleepScreen(vm, padding)
                        Tab.RANKS -> RanksScreen(vm, padding)
                    }
                }
            }
            // Solid strip behind the status bar so scrolled content never runs under the clock.
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
        }
    }
}

/**
 * Fades [content] in whenever [key] changes: 120 ms for a tab, 200 ms plus a 12 dp rise for a page ([slide]).
 * Read in the layer phase, so the frames only redraw; nothing recomposes.
 */
@Composable
private fun Enter(key: Any, slide: Boolean, content: @Composable () -> Unit) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(if (slide) 200 else 120, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            alpha = p
            if (slide) translationY = (1f - p) * 12.dp.toPx()
            compositingStrategy = CompositingStrategy.ModulateAlpha // no offscreen buffer for a full-screen fade
        },
    ) { content() }
}

/** Flat bar: 28 dp top corners, hairline, outlined icons; the active tab gets its filled icon and the Nova dot. */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background) // fills the corner cut-outs, so the list never shows through them
            .background(colors.surfaceContainer, shape)
            .border(1.dp, colors.outlineVariant, shape)
            .pointerInput(Unit) {} // taps on the bar's edges and inset stop here instead of reaching the list behind
            .navigationBarsPadding()
            .heightIn(min = 64.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEach { item -> BarItem(item, item.tab == selected) { onSelect(item.tab) } }
    }
}

@Composable
private fun RowScope.BarItem(item: TabItem, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 32.dp),
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (selected) item.active else item.icon),
            contentDescription = null, // the label names the tab
            modifier = Modifier.size(24.dp),
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(4.dp).background(if (selected) colors.primary else Color.Transparent, CircleShape)) // Nova dot
    }
}

/**
 * Flat snackbar (no shadow): inverseSurface, medium shape, labelLarge, plus its action if it has one (Food's Undo).
 * Every message goes through this one state, so two are never drawn over each other. The host makes it a polite
 * live region.
 */
@Composable
private fun MessageHost(state: SnackbarHostState, modifier: Modifier) {
    SnackbarHost(state, modifier) { data ->
        val colors = MaterialTheme.colorScheme
        val action = data.visuals.actionLabel
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(colors.inverseSurface, MaterialTheme.shapes.medium)
                .padding(start = 16.dp, end = if (action == null) 16.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.labelLarge,
                color = colors.inverseOnSurface,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            )
            if (action != null) {
                TextButton(onClick = data::performAction) { Text(action, style = MaterialTheme.typography.labelLarge, color = colors.inversePrimary) }
            }
        }
    }
}

/**
 * Shows each message from [queue] with [show], which suspends while it is visible; cancelling it hides it.
 * A newer message replaces the visible one, but only after that one has been up for [minMs], so "Workout saved"
 * is still read when "New badge" follows right behind it, and a burst never piles up 4 s snackbars.
 */
internal suspend fun <T : Any> showPaced(queue: ReceiveChannel<T>, minMs: Long, show: suspend (T) -> Unit) {
    coroutineScope {
        var next: T? = null
        while (true) {
            val message = next ?: queue.receive()
            val shown = launch { show(message) }
            withTimeoutOrNull(minMs) { shown.join() }
            next = select {
                shown.onJoin { null } // it timed out or was dismissed: wait for the next one
                queue.onReceive { it } // one is waiting: it takes over now
            }
            shown.cancel()
        }
    }
}
