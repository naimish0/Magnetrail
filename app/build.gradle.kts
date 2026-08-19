plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val googleSampleAppId = "ca-app-pub-3940256099942544~3347511713"
val googleRewardedTestId = "ca-app-pub-3940256099942544/5224354917"
val googleInterstitialTestId = "ca-app-pub-3940256099942544/1033173712"

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val releaseAdMobAppId = providers.gradleProperty("MAGNETRAIL_ADMOB_APP_ID").orNull.orEmpty()
val releaseRewardedId = providers.gradleProperty("MAGNETRAIL_REWARDED_AD_UNIT_ID").orNull.orEmpty()
val releaseInterstitialId = providers.gradleProperty("MAGNETRAIL_INTERSTITIAL_AD_UNIT_ID").orNull.orEmpty()
val releasePrivacyPolicyUrl = providers.gradleProperty("MAGNETRAIL_PRIVACY_POLICY_URL").orNull.orEmpty()
val releaseTargetAudience = providers.gradleProperty("MAGNETRAIL_TARGET_AUDIENCE").orNull.orEmpty()
val releaseLiveAdsRequested = providers.gradleProperty("MAGNETRAIL_ENABLE_LIVE_ADS").orNull == "true"
val releaseMonetizationReady = releaseLiveAdsRequested &&
    releaseAdMobAppId.isNotBlank() && releaseRewardedId.isNotBlank() &&
    releaseInterstitialId.isNotBlank() && releasePrivacyPolicyUrl.isNotBlank() &&
    releaseTargetAudience == "general"

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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["adMobAppId"] = googleSampleAppId
        buildConfigField("boolean", "MONETIZATION_ENABLED", "true")
        buildConfigField("String", "AD_CONFIGURATION_MODE", "google_test".asBuildConfigString())
        buildConfigField("String", "REWARDED_AD_UNIT_ID", googleRewardedTestId.asBuildConfigString())
        buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", googleInterstitialTestId.asBuildConfigString())
        buildConfigField("String", "PRIVACY_POLICY_URL", "".asBuildConfigString())
        buildConfigField("String", "TARGET_AUDIENCE", "unspecified".asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["adMobAppId"] = releaseAdMobAppId.ifBlank { googleSampleAppId }
            buildConfigField("boolean", "MONETIZATION_ENABLED", releaseMonetizationReady.toString())
            buildConfigField(
                "String",
                "AD_CONFIGURATION_MODE",
                (if (releaseMonetizationReady) "production" else "release_blocked").asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "REWARDED_AD_UNIT_ID",
                releaseRewardedId.ifBlank { googleRewardedTestId }.asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "INTERSTITIAL_AD_UNIT_ID",
                releaseInterstitialId.ifBlank { googleInterstitialTestId }.asBuildConfigString(),
            )
            buildConfigField("String", "PRIVACY_POLICY_URL", releasePrivacyPolicyUrl.asBuildConfigString())
            buildConfigField("String", "TARGET_AUDIENCE", releaseTargetAudience.ifBlank { "unspecified" }.asBuildConfigString())
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
    implementation(libs.google.ump)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
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
}
