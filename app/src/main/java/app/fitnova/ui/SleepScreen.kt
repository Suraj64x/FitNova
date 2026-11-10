package app.fitnova.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitnova.AppViewModel
import app.fitnova.Graph
import kotlinx.coroutines.launch

/**
 * TODO(sleep, phase 2): the real Sleep tab (last-night arc, 7-day bars, stat tiles, alarms, permission banner).
 * This stub only proves the data layer: one bed/awake button and last night's total.
 */
@Composable
fun SleepScreen(vm: AppViewModel, padding: PaddingValues) {
    val bedtime by vm.bedtime.collectAsStateWithLifecycle()
    val stats by vm.sleepStats.collectAsStateWithLifecycle()
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 18.dp, bottom = padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sleep", style = MaterialTheme.typography.headlineMedium)
        StatTile("Last night", stats.lastNight?.let { "${it.minutes / 60}h ${it.minutes % 60}m" } ?: "–")
        PrimaryButton(
            if (bedtime == null) "Going to bed" else "I'm awake",
            onClick = {
                // The VM's scope, so switching tabs right after the tap can't cancel the save.
                if (bedtime == null) Graph.sleep.goToBed() else vm.viewModelScope.launch { Graph.sleep.wakeUp("tap") }
            },
        )
    }
}
