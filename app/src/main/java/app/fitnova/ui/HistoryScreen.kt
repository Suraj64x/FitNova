package app.fitnova.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.fitnova.AppViewModel
import app.fitnova.R

/** TODO(shell-home, phase 2): month heatmap, the day list with delete (vm.deleteWorkout), personal bests. */
@Composable
fun HistoryScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeScreen) {
                Icon(painterResource(R.drawable.ic_chevron), contentDescription = "Back", modifier = Modifier.size(22.dp).rotate(180f))
            }
            Text("History", style = MaterialTheme.typography.titleLarge)
        }
        EmptyNote("History is on its way", modifier = Modifier.padding(20.dp))
    }
}
