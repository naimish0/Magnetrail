package com.rameshta.magnetrail

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

internal object CelebrationShare {
    const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.rameshta.magnetrail"

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val SHARE_DIRECTORY = "shared/celebrations"
    private const val SCREENSHOT_FILE = "magnetrail-celebration.png"

    fun storeScreenshot(context: Context, screenshot: Bitmap): Uri {
        val directory = File(context.cacheDir, SHARE_DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) {
            "Unable to create the celebration share directory"
        }
        val screenshotFile = File(directory, SCREENSHOT_FILE)
        FileOutputStream(screenshotFile).use { output ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to encode the celebration screenshot"
            }
        }
        return FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + FILE_PROVIDER_SUFFIX,
            screenshotFile,
        )
    }

    fun sendIntent(contentResolver: ContentResolver, screenshotUri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_SUBJECT, "Magnetrail board cleared")
            putExtra(
                Intent.EXTRA_TEXT,
                "I cleared a Magnetrail board! Can you beat it?\n$PLAY_STORE_URL",
            )
            putExtra(Intent.EXTRA_STREAM, screenshotUri)
            clipData = ClipData.newUri(
                contentResolver,
                "Magnetrail celebration screenshot",
                screenshotUri,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
