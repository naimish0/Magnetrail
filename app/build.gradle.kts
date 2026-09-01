import java.net.URI
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val googleSampleAppId = "ca-app-pub-3940256099942544~3347511713"
val googleRewardedTestId = "ca-app-pub-3940256099942544/5224354917"
val googleInterstitialTestId = "ca-app-pub-3940256099942544/1033173712"
val googleAppOpenTestId = "ca-app-pub-3940256099942544/9257395921"
val blockedReleaseAppId = "ca-app-pub-0000000000000000~0000000000"
val adMobAppIdPattern = Regex("^ca-app-pub-[0-9]{16}~[0-9]{10}$")
val adMobUnitIdPattern = Regex("^ca-app-pub-[0-9]{16}/[0-9]{10}$")

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
val localReleaseProperties = providers.fileContents(
    rootProject.layout.projectDirectory.file("release.properties"),
).asText.orElse("").map { contents ->
    Properties().apply { contents.reader().use(::load) }
}
fun secureBuildValue(name: String): String = providers.environmentVariable(name)
    .orElse(providers.gradleProperty(name))
    .orElse(localReleaseProperties.map { it.getProperty(name).orEmpty() })
    .orNull
    .orEmpty()

val productionReleaseRequested = secureBuildValue("MAGNETRAIL_PRODUCTION_RELEASE") == "true"
val releaseAdMobAppId = secureBuildValue("MAGNETRAIL_ADMOB_APP_ID")
val releaseRewardedId = secureBuildValue("MAGNETRAIL_REWARDED_AD_UNIT_ID")
val releaseInterstitialId = secureBuildValue("MAGNETRAIL_INTERSTITIAL_AD_UNIT_ID")
val releaseAppOpenId = secureBuildValue("MAGNETRAIL_APP_OPEN_AD_UNIT_ID")
val releasePrivacyPolicyUrl = secureBuildValue("MAGNETRAIL_PRIVACY_POLICY_URL")
val releaseTargetAudience = secureBuildValue("MAGNETRAIL_TARGET_AUDIENCE")
val releaseLiveAdsRequested = secureBuildValue("MAGNETRAIL_ENABLE_LIVE_ADS") == "true"
val uploadStorePath = secureBuildValue("MAGNETRAIL_UPLOAD_STORE_FILE")
val uploadKeyAlias = secureBuildValue("MAGNETRAIL_UPLOAD_KEY_ALIAS")
val uploadStorePassword = secureBuildValue("MAGNETRAIL_UPLOAD_STORE_PASSWORD")
val uploadKeyPassword = secureBuildValue("MAGNETRAIL_UPLOAD_KEY_PASSWORD")
val uploadSigningConfigured = listOf(
    uploadStorePath,
    uploadKeyAlias,
    uploadStorePassword,
    uploadKeyPassword,
).all(String::isNotBlank) && file(uploadStorePath).isFile

fun productionConfigurationProblems(): List<String> = buildList {
    if (!releaseLiveAdsRequested) add("MAGNETRAIL_ENABLE_LIVE_ADS must be true")
    if (!adMobAppIdPattern.matches(releaseAdMobAppId) || releaseAdMobAppId == googleSampleAppId) {
        add("MAGNETRAIL_ADMOB_APP_ID must be a non-sample production App ID")
    }
    if (!adMobUnitIdPattern.matches(releaseRewardedId) || releaseRewardedId == googleRewardedTestId) {
        add("MAGNETRAIL_REWARDED_AD_UNIT_ID must be a non-test production unit ID")
    }
    if (!adMobUnitIdPattern.matches(releaseInterstitialId) || releaseInterstitialId == googleInterstitialTestId) {
        add("MAGNETRAIL_INTERSTITIAL_AD_UNIT_ID must be a non-test production unit ID")
    }
    if (!adMobUnitIdPattern.matches(releaseAppOpenId) || releaseAppOpenId == googleAppOpenTestId) {
        add("MAGNETRAIL_APP_OPEN_AD_UNIT_ID must be a non-test production unit ID")
    }
    val policyUri = runCatching { URI(releasePrivacyPolicyUrl) }.getOrNull()
    if (policyUri?.scheme != "https" || policyUri.host.isNullOrBlank()) {
        add("MAGNETRAIL_PRIVACY_POLICY_URL must be a public HTTPS URL")
    }
    if (releaseTargetAudience != "general") {
        add("MAGNETRAIL_TARGET_AUDIENCE must be the owner-reviewed value 'general'")
    }
    if (!uploadSigningConfigured) add("the complete owner-authorized upload signing configuration is missing")
}

val releaseMonetizationReady = productionReleaseRequested && productionConfigurationProblems().isEmpty()
val releaseVersionCode = providers.gradleProperty("magnetrail.versionCode").get().toInt()
val releaseVersionName = providers.gradleProperty("magnetrail.versionName").get()
val humanPlaytestCatalog = providers.gradleProperty("humanPlaytestCatalog").getOrElse("v10")
require(humanPlaytestCatalog in setOf("v6", "v10")) {
    "humanPlaytestCatalog must be v6 or v10"
}

val syncM3Levels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.file("design/context/Magnetrail_Campaign_Levels_v3.json"))
    from(rootProject.layout.projectDirectory.file("design/context/Magnetrail_Daily_Fallbacks_v1.json"))
    from(rootProject.layout.projectDirectory.file("design/context/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json"))
    into(layout.buildDirectory.dir("generated/magnetrailAssets/levels"))
    rename("Magnetrail_Campaign_Levels_v3.json", "magnetrail_campaign_levels_v3.json")
    rename("Magnetrail_Daily_Fallbacks_v1.json", "magnetrail_daily_fallbacks_v1.json")
    rename("INFINITE_CERTIFIED_CATALOG_V1.json", "magnetrail_infinite_catalog_v1.json")
}

val syncV6Pilot by tasks.registering(Sync::class) {
    val playtestCatalog = providers.gradleProperty("v6PlaytestCatalog").getOrElse("calibration")
    require(playtestCatalog in setOf("calibration", "sealed-validation")) {
        "v6PlaytestCatalog must be calibration or sealed-validation"
    }
    from(
        rootProject.layout.projectDirectory.file(
            if (playtestCatalog == "calibration") {
                "design/context/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"
            } else {
                "design/context/content/generator_v6/staging/sealed-validation/GENERATOR_V6_SEALED_VALIDATION_CATALOG.json"
            },
        ),
    )
    inputs.property("v6PlaytestCatalog", playtestCatalog)
    into(layout.buildDirectory.dir("generated/magnetrailDebugAssetsV6/levels"))
    rename("GENERATOR_V6_(CALIBRATION|SEALED_VALIDATION)_CATALOG\\.json", "magnetrail_v6_calibration.json")
}

android {
    namespace = "com.rameshta.magnetrail"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rameshta.magnetrail"
        minSdk = 24
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["adMobAppId"] = googleSampleAppId
        buildConfigField("boolean", "MONETIZATION_ENABLED", "true")
        buildConfigField("String", "AD_CONFIGURATION_MODE", "google_test".asBuildConfigString())
        buildConfigField("String", "ADMOB_APP_ID", googleSampleAppId.asBuildConfigString())
        buildConfigField("String", "REWARDED_AD_UNIT_ID", googleRewardedTestId.asBuildConfigString())
        buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", googleInterstitialTestId.asBuildConfigString())
        buildConfigField("String", "APP_OPEN_AD_UNIT_ID", googleAppOpenTestId.asBuildConfigString())
        buildConfigField("String", "PRIVACY_POLICY_URL", releasePrivacyPolicyUrl.asBuildConfigString())
        buildConfigField("String", "TARGET_AUDIENCE", "unspecified".asBuildConfigString())
        buildConfigField("boolean", "PRODUCTION_RELEASE_REQUESTED", "false")
        buildConfigField("boolean", "UPLOAD_SIGNING_CONFIGURED", "false")
        buildConfigField("String", "HUMAN_PLAYTEST_CATALOG", humanPlaytestCatalog.asBuildConfigString())
    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("upload") {
                storeFile = file(uploadStorePath)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (uploadSigningConfigured) signingConfig = signingConfigs.getByName("upload")
            manifestPlaceholders["adMobAppId"] = releaseAdMobAppId.ifBlank { blockedReleaseAppId }
            buildConfigField("boolean", "MONETIZATION_ENABLED", releaseMonetizationReady.toString())
            buildConfigField(
                "String",
                "AD_CONFIGURATION_MODE",
                (if (releaseMonetizationReady) "production" else "release_blocked").asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "ADMOB_APP_ID",
                releaseAdMobAppId.ifBlank { blockedReleaseAppId }.asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "REWARDED_AD_UNIT_ID",
                releaseRewardedId.asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "INTERSTITIAL_AD_UNIT_ID",
                releaseInterstitialId.asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "APP_OPEN_AD_UNIT_ID",
                releaseAppOpenId.asBuildConfigString(),
            )
            buildConfigField("String", "PRIVACY_POLICY_URL", releasePrivacyPolicyUrl.asBuildConfigString())
            buildConfigField("String", "TARGET_AUDIENCE", releaseTargetAudience.ifBlank { "unspecified" }.asBuildConfigString())
            buildConfigField("boolean", "PRODUCTION_RELEASE_REQUESTED", productionReleaseRequested.toString())
            buildConfigField("boolean", "UPLOAD_SIGNING_CONFIGURED", uploadSigningConfigured.toString())
        }
        create("noAds") {
            initWith(getByName("release"))
            applicationIdSuffix = ".noads"
            versionNameSuffix = "-no-ads"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            manifestPlaceholders["adMobAppId"] = blockedReleaseAppId
            buildConfigField("boolean", "MONETIZATION_ENABLED", "false")
            buildConfigField("String", "AD_CONFIGURATION_MODE", "no_ads".asBuildConfigString())
            buildConfigField("String", "ADMOB_APP_ID", "".asBuildConfigString())
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "".asBuildConfigString())
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "".asBuildConfigString())
            buildConfigField("String", "APP_OPEN_AD_UNIT_ID", "".asBuildConfigString())
            buildConfigField("String", "TARGET_AUDIENCE", "unspecified".asBuildConfigString())
            buildConfigField("boolean", "PRODUCTION_RELEASE_REQUESTED", "false")
            buildConfigField("boolean", "UPLOAD_SIGNING_CONFIGURED", "false")
        }
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    sourceSets {
        named("main") {
            assets.directories.add(
                layout.buildDirectory.dir("generated/magnetrailAssets").get().asFile.absolutePath,
            )
        }
        named("test") {
            resources.directories.add(
                rootProject.layout.buildDirectory.dir("generated/magnetrailTestResources")
                    .get().asFile.absolutePath,
            )
        }
        named("debug") {
            kotlin.directories.add("src/withAds/java")
            assets.directories.add(
                layout.buildDirectory.dir("generated/magnetrailDebugAssetsV6").get().asFile.absolutePath,
            )
        }
        named("release") {
            kotlin.directories.add("src/withAds/java")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(syncM3Levels)
}

tasks.configureEach {
    if (name == "preDebugBuild") dependsOn(syncV6Pilot)
    if (name.endsWith("UnitTestJavaRes")) {
        dependsOn(rootProject.tasks.named("syncDocsTestResources"))
    }
}

val verifyGeneratorV6ReleaseExclusion by tasks.registering {
    group = "verification"
    description = "Prove the V6 blind-playtest catalog is absent from merged release assets."
    dependsOn("mergeReleaseAssets")
    val mergedReleaseAssets = layout.buildDirectory.dir("intermediates/assets/release/mergeReleaseAssets")
    inputs.dir(mergedReleaseAssets)
    doLast {
        val forbidden = mergedReleaseAssets.get().asFile.walkTopDown().filter { file ->
            file.name == "magnetrail_v6_calibration.json"
        }.toList()
        check(forbidden.isEmpty()) { "Debug-only playtest assets leaked into release: $forbidden" }
    }
}

val verifyPrivacyPolicyArtifacts by tasks.registering {
    group = "verification"
    description = "Verify the in-app and deployable privacy-policy artifacts remain truthful and explicitly blocked on owner values."
    val markdown = rootProject.layout.projectDirectory.file("design/context/privacy-policy.md")
    val html = rootProject.layout.projectDirectory.file("docs/index.html")
    val dataSafety = rootProject.layout.projectDirectory.file("design/context/DATA_SAFETY_MAPPING.md")
    val inApp = project.layout.projectDirectory.file(
        "src/main/java/com/rameshta/magnetrail/privacy/PrivacyPolicyScreen.kt",
    )
    inputs.files(markdown, html, dataSafety, inApp)
    doLast {
        val markdownText = markdown.asFile.readText()
        val htmlText = html.asFile.readText()
        val mappingText = dataSafety.asFile.readText()
        val inAppText = inApp.asFile.readText()
        listOf("Google Mobile Ads", "User Messaging Platform").forEach {
            check(it in markdownText) { "Privacy policy is missing installed provider disclosure: $it" }
        }
        check("Firebase Analytics" !in markdownText && "Firebase Crashlytics" !in markdownText) {
            "Privacy policy still discloses Firebase even though it is not shipped in this release cycle"
        }
        check("does not require an account" in markdownText)
        check("Android cloud backup and device-to-device transfer are disabled" in markdownText)
        listOf("Naimish Gupta", "naimish.app@gmail.com", "Sandi, Hardoi, 241403, Uttar Pradesh").forEach {
            check(it in markdownText && it in htmlText && it in inAppText) {
                "Privacy policy is missing configured publisher detail: $it"
            }
        }
        val publicPolicyUrl = "https://naimish0.github.io/Magnetrail/"
        check(publicPolicyUrl in markdownText && publicPolicyUrl in htmlText && publicPolicyUrl in inAppText)
        check("public HTTPS" in markdownText)
        check("Data Safety" in mappingText && "owner must validate" in mappingText)
        listOf("Campaign Level 11", "normal Infinite", "Auto Journey", "Celebration screen", "60 seconds").forEach {
            check(it in markdownText && it in htmlText) { "Privacy policy is missing current ad disclosure: $it" }
        }
        listOf("App Open ad", "per hour", "startup or resume").forEach {
            check(it in markdownText && it in htmlText && it in inAppText) {
                "Privacy policy is missing App Open ad disclosure: $it"
            }
        }
        listOf("Level 11", "normal Infinite", "Auto Journey", "Celebration screen", "60 seconds").forEach {
            check(it in inAppText) { "In-app privacy policy is missing current ad disclosure: $it" }
        }
        check("no daily limit to rewarded-ad use" in markdownText && "no daily limit to rewarded-ad use" in htmlText)
        check("no app daily limit" in inAppText)
    }
}

val verifyProjectPage by tasks.registering {
    group = "verification"
    description = "Verify the public Magnetrail app-details page and its release-facing links."
    val projectPage = rootProject.layout.projectDirectory.file("docs/project.html")
    inputs.file(projectPage)
    doLast {
        val html = projectPage.asFile.readText()
        val requiredContent = listOf(
            "<title>Magnetrail — Android magnetic logic puzzle</title>",
            "https://naimish0.github.io/Magnetrail/project.html",
            "https://play.google.com/store/apps/details?id=com.rameshta.magnetrail",
            "2,205 campaign levels",
            "Naimish Gupta",
            "naimish.app@gmail.com",
            "href=\"index.html\">Privacy Policy</a>",
            "Illustrated gameplay preview",
        )
        requiredContent.forEach { value ->
            check(value in html) { "Public app-details page is missing required content: $value" }
        }
        check("<main id=\"main\">" in html && "</main>" in html)
        check("<script type=\"application/ld+json\">" in html)
        check("MAGNETRAIL_" !in html) { "Internal release configuration leaked into the public app-details page." }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":game-core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.google.mobile.ads)
    releaseImplementation(libs.google.mobile.ads)
    // Mobile Ads 25.4.0 declares WorkManager 2.7.0. Pin the current stable runtime for
    // Android 16 compatibility and to prevent its obsolete Room database from crashing
    // optimized release startup.
    debugImplementation(libs.androidx.work.runtime)
    releaseImplementation(libs.androidx.work.runtime)
    debugImplementation(libs.google.ump)
    releaseImplementation(libs.google.ump)
    implementation(libs.androidx.profileinstaller)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    baselineProfile(project(":baseline-profile"))
}

val releaseConfigurationProblems = productionConfigurationProblems()
val validateReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Reject unsafe production release inputs without printing any secret value."
    inputs.property("releaseVersionCode", releaseVersionCode)
    inputs.property("releaseVersionName", releaseVersionName)
    inputs.property("productionReleaseRequested", productionReleaseRequested)
    inputs.property("releaseConfigurationProblems", releaseConfigurationProblems.joinToString("\u0000"))
    doLast {
        val configuredVersionCode = inputs.properties.getValue("releaseVersionCode").toString().toInt()
        val configuredVersionName = inputs.properties.getValue("releaseVersionName").toString()
        val productionRequested = inputs.properties.getValue("productionReleaseRequested").toString().toBoolean()
        val problems = inputs.properties.getValue("releaseConfigurationProblems").toString()
            .split('\u0000')
            .filter(String::isNotBlank)
        check(configuredVersionCode > 0) { "magnetrail.versionCode must be positive" }
        check(configuredVersionName.isNotBlank()) { "magnetrail.versionName must not be blank" }
        if (productionRequested) {
            check(problems.isEmpty()) {
                "Production release configuration is incomplete:\n- ${problems.joinToString("\n- ")}"
            }
        } else {
            logger.lifecycle(
                "Building a structural, non-uploadable release: live ads, UMP, and owner configuration are disabled.",
            )
        }
    }
}

val releaseMergedManifest = layout.buildDirectory
    .file("intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml")
val releaseAab = layout.buildDirectory.file("outputs/bundle/release/app-release.aab")

val noAdsMergedManifest = layout.buildDirectory
    .file("intermediates/merged_manifests/noAds/processNoAdsManifest/AndroidManifest.xml")
val noAdsApk = layout.buildDirectory.file("outputs/apk/noAds/app-noAds.apk")

val verifyNoAdsVariant by tasks.registering {
    group = "verification"
    description = "Assert the noAds variant cannot initialize or request Google ads."
    dependsOn("assembleNoAds", "processNoAdsManifest")
    inputs.file(noAdsMergedManifest)
    inputs.file(noAdsApk)
    doLast {
        val manifestFile = inputs.files.files.single { it.name == "AndroidManifest.xml" }
        check(manifestFile.isFile) { "Merged noAds manifest was not generated" }
        val manifest = manifestFile.readText()
        val forbiddenManifestEntries = listOf(
            "com.google.android.gms.ads.APPLICATION_ID",
            "com.google.android.gms.ads.AdActivity",
            "com.google.android.gms.ads.AdService",
            "com.google.android.gms.ads.MobileAdsInitProvider",
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_TOPICS",
        )
        forbiddenManifestEntries.forEach { entry ->
            check(entry !in manifest) { "No-ads manifest still contains ad entry: $entry" }
        }
        check("package=\"com.rameshta.magnetrail.noads\"" in manifest) {
            "The noAds build must keep its independently installable application ID"
        }
        val apkFile = inputs.files.files.single { it.extension == "apk" }
        val forbiddenDexReferences = listOf(
            "Lcom/google/android/gms/ads/MobileAds;",
            "Lcom/google/android/gms/ads/AdRequest;",
            "Lcom/google/android/ump/UserMessagingPlatform;",
        )
        ZipFile(apkFile).use { apk ->
            val dexPayloads = apk.entries().asSequence()
                .filter { it.name.endsWith(".dex") }
                .map { String(apk.getInputStream(it).readBytes(), Charsets.ISO_8859_1) }
                .toList()
            forbiddenDexReferences.forEach { reference ->
                check(dexPayloads.none { reference in it }) {
                    "No-ads APK still references an ad SDK type: $reference"
                }
            }
        }
    }
}

val verifyReleaseManifest by tasks.registering {
    group = "verification"
    description = "Assert the merged release manifest has the expected package, SDK, permissions, and component exposure."
    dependsOn("processReleaseManifest")
    inputs.file(releaseMergedManifest)
    inputs.property("googleSampleAppId", googleSampleAppId)
    inputs.property("googleRewardedTestId", googleRewardedTestId)
    inputs.property("googleInterstitialTestId", googleInterstitialTestId)
    doLast {
        val manifestFile = inputs.files.singleFile
        check(manifestFile.isFile) { "Merged release manifest was not generated" }
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifestFile)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val manifest = document.documentElement
        check(manifest.getAttribute("package") == "com.rameshta.magnetrail")
        val usesSdk = document.getElementsByTagName("uses-sdk").item(0) as org.w3c.dom.Element
        check(usesSdk.getAttributeNS(androidNamespace, "minSdkVersion") == "24")
        check(usesSdk.getAttributeNS(androidNamespace, "targetSdkVersion") == "36")

        val application = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        check(application.getAttributeNS(androidNamespace, "debuggable") != "true")
        check(application.getAttributeNS(androidNamespace, "testOnly") != "true")
        check(application.getAttributeNS(androidNamespace, "usesCleartextTraffic") == "false")
        check(application.getAttributeNS(androidNamespace, "allowBackup") == "false")
        check(application.getAttributeNS(androidNamespace, "appCategory") == "game")

        val allowedPermissions = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_TOPICS",
            "android.permission.WAKE_LOCK",
            "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
            "android.permission.FOREGROUND_SERVICE",
            "com.rameshta.magnetrail.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val permissionNodes = document.getElementsByTagName("uses-permission")
        repeat(permissionNodes.length) { index ->
            val node = permissionNodes.item(index) as org.w3c.dom.Element
            val name = node.getAttributeNS(androidNamespace, "name")
            check(name in allowedPermissions) { "Unexpected release permission: $name" }
        }

        listOf("activity", "service", "receiver", "provider").forEach { componentType ->
            val nodes = document.getElementsByTagName(componentType)
            repeat(nodes.length) { index ->
                val component = nodes.item(index) as org.w3c.dom.Element
                val name = component.getAttributeNS(androidNamespace, "name")
                val exported = component.getAttributeNS(androidNamespace, "exported")
                check(exported == "true" || exported == "false") { "$componentType $name has no explicit exported state" }
                if (exported == "true" && name != "com.rameshta.magnetrail.MainActivity") {
                    check(component.getAttributeNS(androidNamespace, "permission").isNotBlank()) {
                        "$componentType $name is exported without a protecting permission"
                    }
                }
            }
        }

        val mergedText = manifestFile.readText()
        check(inputs.properties.getValue("googleSampleAppId").toString() !in mergedText)
        check(inputs.properties.getValue("googleRewardedTestId").toString() !in mergedText)
        check(inputs.properties.getValue("googleInterstitialTestId").toString() !in mergedText)
        check("com.google.firebase" !in mergedText) {
            "Release manifest still contains a deferred diagnostics provider"
        }
    }
}

val verifyFirebaseAbsent by tasks.registering {
    group = "verification"
    description = "Prove the deferred Firebase SDK and configuration are absent from the release graph and artifact."
    dependsOn("bundleRelease", "processReleaseManifest")
    val releaseRuntimeClasspath = configurations.named("releaseRuntimeClasspath")
    val baselineProfile = project.layout.projectDirectory.file(
        "src/release/generated/baselineProfiles/baseline-prof.txt",
    )
    val startupProfile = project.layout.projectDirectory.file(
        "src/release/generated/baselineProfiles/startup-prof.txt",
    )
    inputs.files(releaseRuntimeClasspath, releaseMergedManifest, releaseAab, baselineProfile, startupProfile)
    doLast {
        val taskInputs = inputs.files.files
        val runtimeArchives = taskInputs.filter { it.extension in setOf("aar", "jar") }
        val firebaseArchives = runtimeArchives.filter { archive ->
            archive.name.contains("firebase", ignoreCase = true) ||
                runCatching {
                    ZipFile(archive).use { zip ->
                        zip.entries().asSequence().any { it.name.startsWith("com/google/firebase/") }
                    }
                }.getOrDefault(false)
        }
        check(firebaseArchives.isEmpty()) {
            "Release dependency graph still contains Firebase archives: " +
                firebaseArchives.joinToString { it.name }
        }

        val manifestFile = taskInputs.single { file ->
            file.name == "AndroidManifest.xml" && "merged_manifests/release" in file.invariantSeparatorsPath
        }
        val manifestText = manifestFile.readText()
        check("com.google.firebase" !in manifestText) {
            "Release manifest still contains Firebase configuration or components"
        }
        taskInputs.filter { it.name in setOf("baseline-prof.txt", "startup-prof.txt") }.forEach { profile ->
            check("com/google/firebase" !in profile.readText()) {
                "Generated profile still references Firebase: ${profile.name}"
            }
        }

        val aabFile = taskInputs.single { it.extension == "aab" }
        check(aabFile.isFile) { "Release AAB was not generated" }
        ZipFile(aabFile).use { aab ->
            val packagedReferences = aab.entries().asSequence()
                .filter { entry ->
                    entry.name.endsWith(".dex") || entry.name.endsWith("AndroidManifest.xml")
                }
                .any { entry ->
                    "com/google/firebase" in String(
                        aab.getInputStream(entry).readBytes(),
                        Charsets.ISO_8859_1,
                    )
                }
            check(!packagedReferences) { "Release AAB still contains a Firebase class reference" }
        }
    }
}

tasks.configureEach {
    when (name) {
        "preReleaseBuild" -> dependsOn(validateReleaseConfiguration)
        "bundleRelease" -> dependsOn(verifyReleaseManifest)
        "processDebugUnitTestJavaRes", "processReleaseUnitTestJavaRes" -> {
            mustRunAfter(":level-tools:finalizePhase0")
            mustRunAfter(":level-tools:finalizePhase1")
            mustRunAfter(":level-tools:analyzeGeneratorV61Regression")
        }
    }
}

tasks.register("verifyReleaseReadinessLocal") {
    group = "verification"
    description = "Run repository-local release compilation, shrinker, manifest, lint, tests, debug UI compilation, and privacy checks."
    dependsOn(
        "bundleRelease",
        "lintRelease",
        "testDebugUnitTest",
        "testReleaseUnitTest",
        "testNoAdsUnitTest",
        "compileDebugAndroidTestKotlin",
        verifyGeneratorV6ReleaseExclusion,
        verifyFirebaseAbsent,
        verifyNoAdsVariant,
        verifyPrivacyPolicyArtifacts,
        verifyProjectPage,
        verifyReleaseManifest,
    )
}
