package com.rashed.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rashed.ai.ui.PermissionScreen
import com.rashed.ai.ui.ProviderScreen
import com.rashed.ai.ui.RashedScreen
import com.rashed.ai.ui.SettingsScreen
import com.rashed.ai.viewmodel.RashedViewModel
import com.rashed.ai.viewmodel.ScreenDestination

class MainActivity : ComponentActivity() {

    private val viewModel: RashedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RashedAiTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = Color(0xFF090D16)
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()

                    BackHandler(enabled = currentScreen != ScreenDestination.MAIN) {
                        viewModel.navigateBack()
                    }

                    when (currentScreen) {
                        ScreenDestination.MAIN -> RashedScreen(viewModel = viewModel)
                        ScreenDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                        ScreenDestination.PERMISSIONS -> PermissionScreen(viewModel = viewModel)
                        ScreenDestination.PROVIDER -> ProviderScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}

@Composable
fun RashedAiTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFF38BDF8),
        tertiary = Color(0xFFA855F7),
        background = Color(0xFF090D16),
        surface = Color(0xFF131C2E),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}
