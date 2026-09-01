package com.rameshta.magnetrail.localization

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class AppLanguage(
    val languageTag: String,
    val nativeName: String,
)

object AppLanguageManager {
    val supportedLanguages = listOf(
        AppLanguage("en", "English"),
        AppLanguage("hi-IN", "हिन्दी"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("id-ID", "Bahasa Indonesia"),
        AppLanguage("es-MX", "Español (México)"),
        AppLanguage("tr-TR", "Türkçe"),
        AppLanguage("fil-PH", "Filipino"),
        AppLanguage("th-TH", "ไทย"),
        AppLanguage("de-DE", "Deutsch"),
        AppLanguage("ja-JP", "日本語"),
        AppLanguage("ko-KR", "한국어"),
        AppLanguage("fr-FR", "Français"),
    )

    fun selectedLanguageTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.getSystemService(LocaleManager::class.java)
                .applicationLocales
                .get(0)
                ?.toLanguageTag()
                .orEmpty()
        }
        return preferences(context).getString(LANGUAGE_TAG_KEY, "").orEmpty()
    }

    fun setLanguage(activity: Activity, languageTag: String) {
        require(languageTag.isEmpty() || supportedLanguages.any { it.languageTag == languageTag }) {
            "Unsupported app language: $languageTag"
        }
        preferences(activity).edit().putString(LANGUAGE_TAG_KEY, languageTag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(languageTag)
        } else {
            activity.recreate()
        }
    }

    fun wrapBaseContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val languageTag = selectedLanguageTag(context)
        if (languageTag.isBlank()) {
            context.resources.configuration.locales.get(0)?.let(Locale::setDefault)
            return context
        }
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private const val PREFERENCES_NAME = "magnetrail_language"
    private const val LANGUAGE_TAG_KEY = "language_tag"
}
