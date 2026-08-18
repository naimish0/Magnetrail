package com.rameshta.magnetrail

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rameshta.magnetrail.data.AssetLevelCatalog
import com.rameshta.magnetrail.game.GameViewModel
import com.rameshta.magnetrail.game.MagnetrailApp
import com.rameshta.magnetrail.ui.theme.MagnetrailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MagnetrailTheme {
                val catalogResult = remember {
                    runCatching { AssetLevelCatalog(applicationContext).load() }
                }
                catalogResult.fold(
                    onSuccess = { catalog ->
                        val gameViewModel: GameViewModel = viewModel(
                            factory = GameViewModel.factory(catalog),
                        )
                        val uiState by gameViewModel.uiState.collectAsState()
                        MagnetrailApp(
                            uiState = uiState,
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
