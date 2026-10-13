plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}


android {
    namespace = "com.felixbrucker.torrenthttpdownloader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.felixbrucker.torrenthttpdownloader"
        minSdk = 35
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            storeFile = if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                file(keystorePath)
            } else {
                file("${rootDir}/debug.keystore")
            }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            storeFile = if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                file(keystorePath)
            } else {
                file("${rootDir}/release.keystore")
            }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*_*",
                    "*JsonAdapter*",
                    "com.felixbrucker.torrenthttpdownloader.core.designsystem.**",
                    "com.felixbrucker.torrenthttpdownloader.feature.*.*Screen*",
                    "com.felixbrucker.torrenthttpdownloader.feature.*.*Composables*",
                    "com.felixbrucker.torrenthttpdownloader.feature.*.*BottomSheet*",
                    "com.felixbrucker.torrenthttpdownloader.feature.*.*Dialog*",
                    "com.felixbrucker.torrenthttpdownloader.MainActivity*",
                    "com.felixbrucker.torrenthttpdownloader.AddTorrentActivity*",
                    "com.felixbrucker.torrenthttpdownloader.TorrentHttpDownloaderApp*",
                    "com.felixbrucker.torrenthttpdownloader.IAddTorrentCallback*",
                    "com.felixbrucker.torrenthttpdownloader.ITorrentDownloadService*",
                    "com.felixbrucker.torrenthttpdownloader.AddTorrentParams*",
                    "com.felixbrucker.torrenthttpdownloader.TorrentProgressStats*",
                )
            }
        }
        total {
            verify {
                rule {
                    minBound(30)
                }
            }
        }
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.gson)
    implementation(libs.junrar)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.libtorrent4j.android.arm64)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
