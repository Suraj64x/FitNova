package app.fitnova.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fitnova.AppViewModel
import app.fitnova.domain.Exercise

/** TODO(social, phase 2): three skippable pages (welcome, camera coach, goal + reminder) ending in finishOnboarding(goal). */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyNote("Train smarter at home", body = "Your camera counts reps and checks form.", figure = Exercise.SQUAT)
        PrimaryButton("Get started", onClick = { vm.finishOnboarding(null) })
    }
}
