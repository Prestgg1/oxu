// SPDX-License-Identifier: GPL-3.0-or-later
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

// Set in gradle.properties. Releases are built once per ABI (-Poxu.abis=arm64-v8a), so each
// phone downloads only the native engine it can run.
val oxuAbis = (findProperty("oxu.abis") as String)
    .split(',').map { it.trim() }.filter { it.isNotEmpty() }

// A single-ABI build gets versionCode * 10 + this digit. 64-bit ARM ranks above 32-bit, so a
// phone that runs both gets the arm64 APK. Keep in sync with VercodeOperation in metadata/.
val abiVersionDigits = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

android {
    namespace = "io.github.Prestgg.oxu"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.Prestgg.oxu"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += oxuAbis }
        oxuAbis.singleOrNull()?.let { abi ->
            versionCode = versionCode!! * 10 + abiVersionDigits.getValue(abi)
        }
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            // pdfbox needs BouncyCastle for encrypted PDFs, but never its post-quantum
            // algorithms, whose lookup tables are 8 MB of the APK.
            excludes += "org/bouncycastle/pqc/**"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // HTML parsing for EPUB chapters, HTML files and the web reader (MIT).
    implementation("org.jsoup:jsoup:1.18.1")
    implementation(project(":bergamot"))
    testImplementation("junit:junit:4.13.2")
    // The Android stub of org.json throws in local unit tests.
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
