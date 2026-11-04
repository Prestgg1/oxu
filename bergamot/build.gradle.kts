// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("com.android.library")
}

// ABIs to build the native engine for. Override for faster local builds:
//   ./gradlew assembleDebug -Poxu.abis=arm64-v8a
val oxuAbis = (findProperty("oxu.abis") as String? ?: "arm64-v8a,armeabi-v7a,x86_64")
    .split(',').map { it.trim() }.filter { it.isNotEmpty() }

android {
    namespace = "io.github.Prestgg.oxu.bergamot"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += oxuAbis }
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                arguments += listOf("-DANDROID_STL=c++_static", "-DCMAKE_BUILD_TYPE=Release")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
