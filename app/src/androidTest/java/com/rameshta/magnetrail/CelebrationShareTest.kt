package com.rameshta.magnetrail

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CelebrationShareTest {
    @Test
    fun celebrationShareContainsReadablePngAndPlayStoreUrl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val screenshotUri = try {
            CelebrationShare.storeScreenshot(context, screenshot)
        } finally {
            screenshot.recycle()
        }

        val intent = CelebrationShare.sendIntent(context.contentResolver, screenshotUri)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(screenshotUri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(
            intent.getStringExtra(Intent.EXTRA_TEXT)
                .orEmpty()
                .contains(CelebrationShare.PLAY_STORE_URL),
        )
        context.contentResolver.openInputStream(screenshotUri).use { input ->
            assertNotNull(input)
            val header = ByteArray(PNG_SIGNATURE.size)
            assertEquals(PNG_SIGNATURE.size, input?.read(header))
            assertEquals(PNG_SIGNATURE, header.map { byte -> byte.toInt() and 0xff })
        }
    }

    private companion object {
        val PNG_SIGNATURE = listOf(137, 80, 78, 71, 13, 10, 26, 10)
    }
}
