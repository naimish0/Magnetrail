package com.rameshta.magnetrail.privacy

import android.net.Uri
import androidx.core.net.toUri

object ExternalUrlPolicy {
    fun httpsUriOrNull(value: String): Uri? {
        if (!isSafeHttpsUrl(value)) return null
        return value.toUri()
    }

    fun isSafeHttpsUrl(value: String): Boolean {
        if (value.isBlank() || value.any(Char::isWhitespace)) return false
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }
}
