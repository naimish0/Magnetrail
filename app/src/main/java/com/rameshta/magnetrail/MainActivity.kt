package com.rameshta.magnetrail

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rameshta.magnetrail.data.AssetLevelCatalog
import com.rameshta.magnetrail.data.DataStoreProgressRepository
import com.rameshta.magnetrail.feedback.FeedbackController
import com.rameshta.magnetrail.feedback.SynthSoundController
import com.rameshta.magnetrail.feedback.ViewHapticController
import com.rameshta.magnetrail.game.GameViewModel
import com.rameshta.magnetrail.game.MagnetrailApp
import com.rameshta.magnetrail.ui.theme.MagnetrailTheme

class MainActivity : ComponentActivity() {
    private lateinit var feedbackController: FeedbackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        feedbackController = FeedbackController(
            soundController = SynthSoundController(),
            hapticController = ViewHapticController(window.decorView),
            enabled = !isRunningInstrumentedTest(),
        )
        setContent {
            MagnetrailTheme {
                val catalogResult = remember {
                    runCatching { AssetLevelCatalog(applicationContext).load() }
                }
                catalogResult.fold(
                    onSuccess = { catalog ->
                        val repository = remember(catalog) {
                            DataStoreProgressRepository(
                                context = applicationContext,
                                catalog = catalog,
                                defaultReducedMotion = systemPrefersReducedMotion(),
                            )
                        }
                        val gameViewModel: GameViewModel = viewModel(
                            factory = GameViewModel.factory(
                                catalog = catalog,
                                repository = repository,
                                debugUnlockAll = BuildConfig.DEBUG,
                            ),
                        )
                        val uiState by gameViewModel.uiState.collectAsState()
                        val currentSettings by rememberUpdatedState(uiState.settings)
                        LaunchedEffect(gameViewModel) {
                            gameViewModel.feedbackEvents.collect { event ->
                                feedbackController.handle(event, currentSettings)
                            }
                        }
                        MagnetrailApp(
                            uiState = uiState,
                            debugUnlockAll = gameViewModel.debugUnlockAll,
                            onAction = gameViewModel::onAction,
                        )
                    },
                    onFailure = { error ->
                        CatalogErrorScreen(error)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        if (::feedbackController.isInitialized) feedbackController.close()
        super.onDestroy()
    }

    private fun systemPrefersReducedMotion(): Boolean = if (Build.VERSION.SDK_INT >= 26) {
        !ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    private fun isRunningInstrumentedTest(): Boolean = runCatching {
        val registry = Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        registry.getMethod("getInstrumentation").invoke(null) != null
    }.getOrDefault(false)
}

@androidx.compose.runtime.Composable
private fun CatalogErrorScreen(error: Throwable) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Level catalog unavailable", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = error.message ?: "The canonical level asset could not be loaded.",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
