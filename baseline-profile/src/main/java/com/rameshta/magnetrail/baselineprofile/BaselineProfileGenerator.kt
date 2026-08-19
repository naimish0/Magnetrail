package com.rameshta.magnetrail.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForDescription("Play current level")
    }

    @Test
    fun criticalUserJourneys() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()

        device.clickDescription("Play current level")
        device.waitForDescriptionPrefix("Arrow ")?.click()
        device.wait(Until.hasObject(By.text("Board cleared")), TIMEOUT_MILLIS)
        device.findObject(By.desc("Return home"))?.click()
        device.waitForDescription("Open level selection")

        device.clickDescription("Open level selection")
        device.wait(Until.hasObject(By.text("Campaign")), TIMEOUT_MILLIS)
        device.findObject(By.scrollable(true))?.apply {
            repeat(5) { scroll(Direction.DOWN, 0.9f) }
        }
        device.findObject(By.desc("Close level selection"))?.click()
        device.wait(Until.hasObject(By.text("DAILY CHALLENGE")), TIMEOUT_MILLIS)
        device.findObject(By.text("DAILY CHALLENGE"))?.click()
        device.wait(Until.hasObject(By.textStartsWith("Daily Challenge")), TIMEOUT_MILLIS)
    }

    private fun UiDevice.waitForDescription(description: String) =
        wait(Until.findObject(By.desc(description)), TIMEOUT_MILLIS)

    private fun UiDevice.waitForDescriptionPrefix(prefix: String) =
        wait(Until.findObject(By.descStartsWith(prefix)), TIMEOUT_MILLIS)

    private fun UiDevice.clickDescription(description: String) {
        checkNotNull(waitForDescription(description)) { "Missing UI object '$description'" }.click()
    }

    private companion object {
        const val PACKAGE_NAME = "com.rameshta.magnetrail"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
