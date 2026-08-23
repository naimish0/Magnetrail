package com.rameshta.magnetrail.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.descStartsWith("Play Level ")), HOME_READY_TIMEOUT_MILLIS)) {
            "Home screen did not become ready during the startup benchmark"
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.rameshta.magnetrail"
        const val HOME_READY_TIMEOUT_MILLIS = 10_000L
    }
}
