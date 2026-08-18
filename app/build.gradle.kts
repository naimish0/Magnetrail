plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val syncPrototypeLevels by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.file("docs/Magnetrail_Prototype_Levels_v1.json"))
    into(layout.buildDirectory.dir("generated/magnetrailAssets/levels"))
    rename { "magnetrail_prototype_levels_v1.json" }
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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
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
    dependsOn(syncPrototypeLevels)
}

dependencies {
    implementation(project(":game-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
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
