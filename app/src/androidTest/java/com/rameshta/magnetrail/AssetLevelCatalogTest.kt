package com.rameshta.magnetrail

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.magnetrail.data.AssetLevelCatalog
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetLevelCatalogTest {
    @Test
    fun v10HumanPlaytestReusesTheParsedCampaign() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalogs = AssetLevelCatalog(context)

        val campaign = catalogs.load()
        val humanPlaytest = catalogs.loadHumanPlaytestCatalog("v10")

        assertSame(campaign, humanPlaytest)
    }
}
