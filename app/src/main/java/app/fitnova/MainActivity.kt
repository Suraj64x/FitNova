package app.fitnova

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import app.fitnova.ui.AppRoot
import app.fitnova.ui.FitNovaTheme
import app.fitnova.ui.isDark

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The theme's window background follows the system, not the in-app choice: repaint it before the first
        // frame so picking Dark on a light phone (or the reverse) doesn't flash.
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = vm.theme == ThemeMode.DARK || (vm.theme == ThemeMode.SYSTEM && systemDark)
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF0B0C0F.toInt() else 0xFFF5F6F8.toInt()))
        setContent {
            val inAppDark = isDark(vm.theme)
            // Keep status/navigation bar icons readable when the in-app theme differs from the system one.
            DisposableEffect(inAppDark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { inAppDark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            FitNovaTheme(inAppDark) { AppRoot(vm) }
        }
    }
}
