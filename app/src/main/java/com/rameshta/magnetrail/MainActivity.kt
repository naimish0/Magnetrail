package com.rameshta.magnetrail

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rameshta.magnetrail.ads.MonetizationController
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.autojourney.AutoJourneyCoordinator
import com.rameshta.magnetrail.autojourney.DataStoreAutoJourneyRepository
import com.rameshta.magnetrail.crash.CrashKey
import com.rameshta.magnetrail.data.AssetLevelCatalog
import com.rameshta.magnetrail.data.DataStoreProgressRepository
import com.rameshta.magnetrail.daily.DailyChallengeService
import com.rameshta.magnetrail.feedback.FeedbackController
import com.rameshta.magnetrail.feedback.SynthSoundController
import com.rameshta.magnetrail.feedback.ViewHapticController
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.GameMode
import com.rameshta.magnetrail.game.GameViewModel
import com.rameshta.magnetrail.game.MagnetrailApp
import com.rameshta.magnetrail.infinite.InfiniteModeService
import com.rameshta.magnetrail.playtest.DataStoreHumanPlaytestRepository
import com.rameshta.magnetrail.playtest.HumanPlaytestExport
import com.rameshta.magnetrail.privacy.ExternalUrlPolicy
import com.rameshta.magnetrail.ui.theme.MagnetrailTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var feedbackController: FeedbackController
    private val startupStartedMillis = SystemClock.elapsedRealtime()

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
                val catalogResult by produceState<Result<StartupCatalogs>?>(initialValue = null) {
                    value = try {
                        Result.success(
                            withContext(Dispatchers.IO) {
                                loadStartupCatalogs()
                            },
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                }
                catalogResult?.fold(
                    onSuccess = { catalogs ->
                        val catalog = catalogs.campaign
                        val dailyFallbacks = catalogs.dailyFallbacks
                        val infiniteCatalog = catalogs.infinite
                        val humanPlaytestCatalog = catalogs.humanPlaytest
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
                        LaunchedEffect(
                            uiState.isComplete,
                            uiState.completionPersisted,
                            uiState.gameMode,
                            uiState.currentLevel.id,
                            uiState.infinitePuzzleId,
                            uiState.autoJourneyInternalId,
                        ) {
                            if (uiState.isComplete &&
                                uiState.completionPersisted &&
                                uiState.gameMode in setOf(GameMode.CAMPAIGN, GameMode.INFINITE)
                            ) {
                                monetizationController.showInterstitialForCompletion(
                                    activity = this@MainActivity,
                                    uiState = uiState,
                                )
                            }
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
                                gameViewModel.onAction(GameAction.NextLevel)
                            },
                            onShareCelebration = ::shareCelebration,
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
                        LaunchedEffect(Unit) {
                            reportFullyDrawn()
                            if (BuildConfig.DEBUG) {
                                Log.i(
                                    STARTUP_LOG_TAG,
                                    "home_ready_ms=${SystemClock.elapsedRealtime() - startupStartedMillis}",
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        CatalogErrorScreen(error)
                    },
                ) ?: CatalogLoadingScreen()
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
            services.appOpenAdService.preloadIfAllowed()
        }
        lifecycleScope.launch {
            services.appOpenAdService.showIfEligible(this@MainActivity)
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

    private fun loadStartupCatalogs(): StartupCatalogs {
        val startedMillis = SystemClock.elapsedRealtime()
        val assets = AssetLevelCatalog(applicationContext)
        val campaign = assets.load()
        return StartupCatalogs(
            campaign = campaign,
            dailyFallbacks = assets.loadDailyFallbacks(),
            infinite = assets.loadInfiniteCatalog(),
            humanPlaytest = if (BuildConfig.DEBUG) {
                assets.loadHumanPlaytestCatalog(BuildConfig.HUMAN_PLAYTEST_CATALOG)
            } else {
                campaign
            },
        ).also {
            if (BuildConfig.DEBUG) {
                Log.i(
                    STARTUP_LOG_TAG,
                    "catalog_load_ms=${SystemClock.elapsedRealtime() - startedMillis}",
                )
            }
        }
    }

    private fun shareCelebration() {
        val contentView = window.decorView.rootView
        contentView.post {
            val screenshot = runCatching {
                check(contentView.width > 0 && contentView.height > 0) {
                    "Celebration screen is not ready to capture"
                }
                contentView.drawToBitmap(Bitmap.Config.ARGB_8888)
            }.getOrElse {
                showCelebrationShareError()
                return@post
            }
            lifecycleScope.launch {
                try {
                    val imageUri = withContext(Dispatchers.IO) {
                        CelebrationShare.storeScreenshot(this@MainActivity, screenshot)
                    }
                    startActivity(
                        Intent.createChooser(
                            CelebrationShare.sendIntent(contentResolver, imageUri),
                            getString(R.string.share_celebration_chooser),
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    showCelebrationShareError()
                } finally {
                    screenshot.recycle()
                }
            }
        }
    }

    private fun showCelebrationShareError() {
        Toast.makeText(this, R.string.share_celebration_error, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val STARTUP_LOG_TAG = "MagnetrailStartup"
    }
}

private data class StartupCatalogs(
    val campaign: com.rameshta.magnetrail.core.level.LevelCatalog,
    val dailyFallbacks: com.rameshta.magnetrail.core.level.LevelCatalog,
    val infinite: com.rameshta.magnetrail.core.level.LevelCatalog,
    val humanPlaytest: com.rameshta.magnetrail.core.level.LevelCatalog,
)

@androidx.compose.runtime.Composable
private fun CatalogLoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading campaign…",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
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
