package com.rameshta.magnetrail

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.rameshta.magnetrail.ads.MonetizationController
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.crash.CrashKey
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rameshta.magnetrail.data.AssetLevelCatalog
import com.rameshta.magnetrail.data.DataStoreProgressRepository
import com.rameshta.magnetrail.daily.DailyChallengeService
import com.rameshta.magnetrail.feedback.FeedbackController
import com.rameshta.magnetrail.feedback.SynthSoundController
import com.rameshta.magnetrail.feedback.ViewHapticController
import com.rameshta.magnetrail.game.GameViewModel
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.MagnetrailApp
import com.rameshta.magnetrail.game.GameMode
import com.rameshta.magnetrail.infinite.InfiniteModeService
import com.rameshta.magnetrail.playtest.DataStoreHumanPlaytestRepository
import com.rameshta.magnetrail.playtest.HumanPlaytestExport
import com.rameshta.magnetrail.autojourney.AutoJourneyCoordinator
import com.rameshta.magnetrail.autojourney.DataStoreAutoJourneyRepository
import com.rameshta.magnetrail.privacy.ExternalUrlPolicy
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
        val services = (application as MagnetrailApplication).m4Services
        val privacyPolicyUri = ExternalUrlPolicy.httpsUriOrNull(BuildConfig.PRIVACY_POLICY_URL)
        setContent {
            MagnetrailTheme {
                val catalogResult = remember {
                    runCatching {
                        val assets = AssetLevelCatalog(applicationContext)
                        val campaign = assets.load()
                        listOf(
                            campaign,
                            assets.loadDailyFallbacks(),
                            assets.loadInfiniteCatalog(),
                            if (BuildConfig.DEBUG) {
                                assets.loadHumanPlaytestCatalog(BuildConfig.HUMAN_PLAYTEST_CATALOG)
                            } else {
                                campaign
                            },
                        )
                    }
                }
                catalogResult.fold(
                    onSuccess = { catalogs ->
                        val catalog = catalogs[0]
                        val dailyFallbacks = catalogs[1]
                        val infiniteCatalog = catalogs[2]
                        val humanPlaytestCatalog = catalogs[3]
                        val repository = remember(catalog) {
                            DataStoreProgressRepository(
                                context = applicationContext,
                                catalog = catalog,
                                defaultReducedMotion = systemPrefersReducedMotion(),
                                crashReporter = services.crashReporter,
                            )
                        }
                        val dailyChallengeService = remember(catalog, dailyFallbacks) {
                            DailyChallengeService(catalog.levels, dailyFallbacks)
                        }
                        val infiniteModeService = remember(infiniteCatalog) {
                            InfiniteModeService(infiniteCatalog)
                        }
                        val humanPlaytestRepository = remember {
                            DataStoreHumanPlaytestRepository(applicationContext)
                        }
                        val autoJourneyCoordinator = remember(catalog, dailyFallbacks, infiniteCatalog) {
                            AutoJourneyCoordinator(
                                repository = DataStoreAutoJourneyRepository(applicationContext),
                                shippedCatalogs = listOf(catalog, dailyFallbacks, infiniteCatalog, humanPlaytestCatalog),
                            )
                        }
                        val gameViewModel: GameViewModel = viewModel(
                            factory = GameViewModel.factory(
                                catalog = catalog,
                                repository = repository,
                                dailyChallengeService = dailyChallengeService,
                                infiniteModeService = infiniteModeService,
                                debugUnlockAll = BuildConfig.DEBUG,
                                analytics = services.analytics,
                                crashReporter = services.crashReporter,
                                humanPlaytestRepository = humanPlaytestRepository,
                                humanPlaytestEnabled = BuildConfig.DEBUG,
                                humanPlaytestCatalog = humanPlaytestCatalog,
                                autoJourneyCoordinator = autoJourneyCoordinator,
                            ),
                        )
                        val uiState by gameViewModel.uiState.collectAsState()
                        var pendingHumanPlaytestExport by remember(gameViewModel) {
                            mutableStateOf<HumanPlaytestExport?>(null)
                        }
                        val humanPlaytestExportLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("text/csv"),
                        ) { uri ->
                            val export = pendingHumanPlaytestExport
                            pendingHumanPlaytestExport = null
                            val message = when {
                                uri == null -> "Export cancelled."
                                export == null -> "Unable to export: no result data was prepared."
                                else -> runCatching {
                                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                                        writer.write(export.content)
                                    } ?: error("The selected file could not be opened")
                                }.fold(
                                    onSuccess = { "Results exported successfully." },
                                    onFailure = { "Unable to export results: ${it.message ?: "unknown error"}" },
                                )
                            }
                            gameViewModel.onAction(GameAction.HumanPlaytestExportFinished(message))
                        }
                        val privacyState by services.privacyManager.state.collectAsState()
                        val rewardedAdState by services.rewardedAdService.state.collectAsState()
                        val monetizationController = remember(repository) {
                            MonetizationController(
                                repository = repository,
                                privacyManager = services.privacyManager,
                                rewardedAdService = services.rewardedAdService,
                                interstitialAdService = services.interstitialAdService,
                                coordinator = services.coordinator,
                                analytics = services.analytics,
                                crashReporter = services.crashReporter,
                                clock = services.clock,
                            )
                        }
                        val currentSettings by rememberUpdatedState(uiState.settings)
                        val currentUiState by rememberUpdatedState(uiState)
                        LaunchedEffect(Unit) {
                            services.privacyManager.refresh(this@MainActivity)
                        }
                        LaunchedEffect(uiState.settings.diagnosticsEnabled, privacyState) {
                            services.observability.apply(uiState.settings.diagnosticsEnabled, privacyState)
                        }
                        LaunchedEffect(uiState.destination, uiState.currentLevel.id, privacyState.flowResult) {
                            services.crashReporter.setKey(CrashKey.SCREEN, uiState.destination.name.lowercase())
                            services.crashReporter.setKey(
                                CrashKey.CONTENT_PROFILE,
                                when (uiState.gameMode) {
                                    GameMode.CAMPAIGN -> uiState.currentLevel.id
                                    GameMode.DAILY -> "daily"
                                    GameMode.INFINITE -> "infinite"
                                    GameMode.PLAYTEST -> "human_playtest"
                                },
                            )
                            services.crashReporter.setKey(CrashKey.CONSENT_STATE, privacyState.flowResult.name.lowercase())
                        }
                        LaunchedEffect(gameViewModel) {
                            gameViewModel.feedbackEvents.collect { event ->
                                feedbackController.handle(event, currentSettings)
                            }
                        }
                        LaunchedEffect(gameViewModel, humanPlaytestExportLauncher) {
                            gameViewModel.humanPlaytestExports.collect { export ->
                                pendingHumanPlaytestExport = export
                                humanPlaytestExportLauncher.launch(export.fileName)
                            }
                        }
                        MagnetrailApp(
                            uiState = uiState,
                            debugUnlockAll = gameViewModel.debugUnlockAll,
                            onAction = gameViewModel::onAction,
                            rewardedOffer = remember(
                                uiState.progress.monetization,
                                privacyState,
                                rewardedAdState,
                            ) { monetizationController.rewardedOffer(uiState.progress) },
                            rewardedSkipOffer = remember(
                                privacyState,
                                rewardedAdState,
                            ) { monetizationController.rewardedSkipOffer() },
                            onRewardedHint = {
                                lifecycleScope.launch {
                                    monetizationController.requestRewardedHint(
                                        activity = this@MainActivity,
                                        uiState = currentUiState,
                                        onCreditReady = {
                                            gameViewModel.onAction(GameAction.UseRewardedHintCredit(it))
                                        },
                                        onMessage = {
                                            gameViewModel.onAction(GameAction.ShowHintMessage(it))
                                        },
                                    )
                                }
                            },
                            onRewardedSkip = {
                                lifecycleScope.launch {
                                    monetizationController.requestRewardedSkip(
                                        activity = this@MainActivity,
                                        uiState = currentUiState,
                                        onGranted = {
                                            gameViewModel.onAction(GameAction.ApplyRewardedSkip(it))
                                        },
                                        onMessage = {
                                            gameViewModel.onAction(GameAction.ShowHintMessage(it))
                                        },
                                    )
                                }
                            },
                            onNextLevel = {
                                if (currentUiState.gameMode == GameMode.INFINITE && !currentUiState.isAutoJourney ||
                                    currentUiState.gameMode == GameMode.PLAYTEST
                                ) {
                                    gameViewModel.onAction(GameAction.NextLevel)
                                } else {
                                    lifecycleScope.launch {
                                        monetizationController.nextLevel(
                                            activity = this@MainActivity,
                                            uiState = currentUiState,
                                            navigate = { gameViewModel.onAction(GameAction.NextLevel) },
                                        )
                                    }
                                }
                            },
                            privacyOptionsRequired = privacyState.privacyOptionsRequired,
                            privacyPolicyUrl = privacyPolicyUri?.toString(),
                            showPrivacyPolicyPlaceholder = BuildConfig.DEBUG && privacyPolicyUri == null,
                            onPrivacyOptions = {
                                services.analytics.track(AnalyticsEvent.PrivacyOptionsOpen)
                                services.privacyManager.showPrivacyOptions(this@MainActivity)
                            },
                            onPrivacyPolicy = {
                                if (privacyPolicyUri == null) {
                                    gameViewModel.onAction(GameAction.OpenPrivacyPolicy)
                                } else {
                                    runCatching {
                                        startActivity(
                                            Intent(Intent.ACTION_VIEW, privacyPolicyUri)
                                                .addCategory(Intent.CATEGORY_BROWSABLE),
                                        )
                                    }.onFailure {
                                        gameViewModel.onAction(GameAction.OpenPrivacyPolicy)
                                    }
                                }
                            },
                            showHumanPlaytest = gameViewModel.humanPlaytestEnabled,
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

    override fun onStart() {
        super.onStart()
        (application as MagnetrailApplication).m4Services.clock.setForeground(true)
    }

    override fun onResume() {
        super.onResume()
        val services = (application as MagnetrailApplication).m4Services
        if (services.privacyManager.state.value.canRequestAds) {
            services.rewardedAdService.preloadIfAllowed()
            services.interstitialAdService.preloadIfAllowed()
        }
    }

    override fun onStop() {
        (application as MagnetrailApplication).m4Services.clock.setForeground(false)
        super.onStop()
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
