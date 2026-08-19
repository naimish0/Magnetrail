import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.kotlin.compose)
}

val googleSampleAppId = "ca-app-pub-3940256099942544~3347511713"
val googleRewardedTestId = "ca-app-pub-3940256099942544/5224354917"
val googleInterstitialTestId = "ca-app-pub-3940256099942544/1033173712"
val blockedReleaseAppId = "ca-app-pub-0000000000000000~0000000000"
val adMobAppIdPattern = Regex("^ca-app-pub-[0-9]{16}~[0-9]{10}$")
val adMobUnitIdPattern = Regex("^ca-app-pub-[0-9]{16}/[0-9]{10}$")

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
fun secureBuildValue(name: String): String = providers.environmentVariable(name)
    .orElse(providers.gradleProperty(name))
    .orNull
    .orEmpty()

val productionReleaseRequested = secureBuildValue("MAGNETRAIL_PRODUCTION_RELEASE") == "true"
val releaseAdMobAppId = secureBuildValue("MAGNETRAIL_ADMOB_APP_ID")
val releaseRewardedId = secureBuildValue("MAGNETRAIL_REWARDED_AD_UNIT_ID")
val releaseInterstitialId = secureBuildValue("MAGNETRAIL_INTERSTITIAL_AD_UNIT_ID")
val releasePrivacyPolicyUrl = secureBuildValue("MAGNETRAIL_PRIVACY_POLICY_URL")
val releaseTargetAudience = secureBuildValue("MAGNETRAIL_TARGET_AUDIENCE")
val releaseLiveAdsRequested = secureBuildValue("MAGNETRAIL_ENABLE_LIVE_ADS") == "true"
val releaseFirebaseConfigured = file("google-services.json").isFile
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

if (releaseFirebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

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
    val policyUri = runCatching { URI(releasePrivacyPolicyUrl) }.getOrNull()
    if (policyUri?.scheme != "https" || policyUri.host.isNullOrBlank()) {
        add("MAGNETRAIL_PRIVACY_POLICY_URL must be a public HTTPS URL")
    }
    if (releaseTargetAudience != "general") {
        add("MAGNETRAIL_TARGET_AUDIENCE must be the owner-reviewed value 'general'")
    }
    if (!releaseFirebaseConfigured) add("app/google-services.json is missing")
    if (!uploadSigningConfigured) add("the complete owner-authorized upload signing configuration is missing")
}

val releaseMonetizationReady = productionReleaseRequested && productionConfigurationProblems().isEmpty()
val releaseVersionCode = providers.gradleProperty("magnetrail.versionCode").get().toInt()
val releaseVersionName = providers.gradleProperty("magnetrail.versionName").get()

val syncM3Levels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.file("docs/Magnetrail_Campaign_Levels_v3.json"))
    from(rootProject.layout.projectDirectory.file("docs/Magnetrail_Daily_Fallbacks_v1.json"))
    into(layout.buildDirectory.dir("generated/magnetrailAssets/levels"))
    rename("Magnetrail_Campaign_Levels_v3.json", "magnetrail_campaign_levels_v3.json")
    rename("Magnetrail_Daily_Fallbacks_v1.json", "magnetrail_daily_fallbacks_v1.json")
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
        buildConfigField("String", "PRIVACY_POLICY_URL", "".asBuildConfigString())
        buildConfigField("String", "TARGET_AUDIENCE", "unspecified".asBuildConfigString())
        buildConfigField("boolean", "PRODUCTION_RELEASE_REQUESTED", "false")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", "false")
        buildConfigField("boolean", "UPLOAD_SIGNING_CONFIGURED", "false")
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
            buildConfigField("String", "PRIVACY_POLICY_URL", releasePrivacyPolicyUrl.asBuildConfigString())
            buildConfigField("String", "TARGET_AUDIENCE", releaseTargetAudience.ifBlank { "unspecified" }.asBuildConfigString())
            buildConfigField("boolean", "PRODUCTION_RELEASE_REQUESTED", productionReleaseRequested.toString())
            buildConfigField("boolean", "FIREBASE_CONFIGURED", releaseFirebaseConfigured.toString())
            buildConfigField("boolean", "UPLOAD_SIGNING_CONFIGURED", uploadSigningConfigured.toString())
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
            resources.directories.add(rootProject.file("docs").absolutePath)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(syncM3Levels)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":game-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.mobile.ads)
    // Mobile Ads 25.4.0 declares WorkManager 2.7.0. Pin the current stable runtime for
    // Android 16 compatibility and to prevent its obsolete Room database from crashing
    // optimized release startup.
    implementation(libs.androidx.work.runtime)
    implementation(libs.google.ump)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
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

val verifyReleaseManifest by tasks.registering {
    group = "verification"
    description = "Assert the merged release manifest has the expected package, SDK, permissions, and component exposure."
    dependsOn("processReleaseManifest")
    doLast {
        val manifestFile = layout.buildDirectory
            .file("intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml")
            .get().asFile
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
        check(googleSampleAppId !in mergedText)
        check(googleRewardedTestId !in mergedText)
        check(googleInterstitialTestId !in mergedText)
    }
}

tasks.configureEach {
    when (name) {
        "preReleaseBuild" -> dependsOn(validateReleaseConfiguration)
        "bundleRelease" -> dependsOn(verifyReleaseManifest)
        "processDebugUnitTestJavaRes", "processReleaseUnitTestJavaRes" -> {
            mustRunAfter(":level-tools:finalizePhase0")
            mustRunAfter(":level-tools:finalizePhase1")
        }
    }
}
