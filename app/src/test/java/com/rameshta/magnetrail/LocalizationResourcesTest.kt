package com.rameshta.magnetrail

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourcesTest {
    @Test
    fun everySupportedLocaleHasEveryTranslatableStringWithMatchingFormatArguments() {
        val resources = File("src/main/res")
        val base = readStrings(File(resources, "values/strings.xml"))
            .filterValues { it.translatable }

        localeDirectories.forEach { directory ->
            val localized = readStrings(File(resources, "$directory/strings.xml"))
            assertEquals("String keys differ for $directory", base.keys, localized.keys)
            base.forEach { (name, source) ->
                assertEquals(
                    "Format arguments differ for $directory/$name",
                    formatArguments(source.value),
                    formatArguments(localized.getValue(name).value),
                )
            }
        }
    }

    @Test
    fun localeConfigAdvertisesExactlyTheSupportedAppLanguages() {
        val file = File("src/main/res/xml/locales_config.xml")
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
        val localeNodes = document.getElementsByTagName("locale")
        val configured = buildSet {
            repeat(localeNodes.length) { index ->
                add(localeNodes.item(index).attributes.getNamedItemNS(ANDROID_NAMESPACE, "name").nodeValue)
            }
        }
        assertEquals(languageTags, configured)
        assertTrue("English must remain the default fallback", "en" in configured)
    }

    private fun readStrings(file: File): Map<String, ResourceString> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            repeat(nodes.length) { index ->
                val node = nodes.item(index)
                val attributes = node.attributes
                put(
                    attributes.getNamedItem("name").nodeValue,
                    ResourceString(
                        value = node.textContent,
                        translatable = attributes.getNamedItem("translatable")?.nodeValue != "false",
                    ),
                )
            }
        }
    }

    private fun formatArguments(value: String): List<String> = FORMAT_ARGUMENT
        .findAll(value)
        .map { it.value }
        .sorted()
        .toList()

    private data class ResourceString(val value: String, val translatable: Boolean)

    private companion object {
        val localeDirectories = setOf(
            "values-hi",
            "values-pt-rBR",
            "values-in",
            "values-es-rMX",
            "values-tr",
            "values-fil",
            "values-th",
            "values-de",
            "values-ja",
            "values-ko",
            "values-fr",
        )
        val languageTags = setOf(
            "en",
            "hi-IN",
            "pt-BR",
            "id-ID",
            "es-MX",
            "tr-TR",
            "fil-PH",
            "th-TH",
            "de-DE",
            "ja-JP",
            "ko-KR",
            "fr-FR",
        )
        val FORMAT_ARGUMENT = Regex("%\\d+\\$[ds]")
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
