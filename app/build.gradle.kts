import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.freckles.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.freckles.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TMDB_API_KEY", "\"${localProps.getProperty("TMDB_API_KEY", "")}\"")
        buildConfigField("String", "IGDB_CLIENT_ID", "\"${localProps.getProperty("IGDB_CLIENT_ID", "")}\"")
        buildConfigField("String", "IGDB_CLIENT_SECRET", "\"${localProps.getProperty("IGDB_CLIENT_SECRET", "")}\"")
    }

    // Firma di release: legge keystore e password da local.properties (mai nel repo).
    val releaseStore = localProps.getProperty("RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Il lint di AGP 8.7 va in crash con il JDK 25 di Android Studio ("IllegalArgumentException: 25.0.4").
    lint { checkReleaseBuilds = false }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.ffmpeg)
    implementation(libs.coil.compose)
    implementation(libs.work.runtime)
    implementation(libs.datastore)

    testImplementation("junit:junit:4.13.2")
    // Test sull'emulatore (database in memoria, non toccano i dati dell'app).
    // NON usare connectedDebugAndroidTest: alla fine disinstalla l'app e ne cancella i dati. Invece:
    //   ./gradlew installDebug installDebugAndroidTest
    //   adb shell am instrument -w com.freckles.tv.test/androidx.test.runner.AndroidJUnitRunner
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
