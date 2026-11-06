import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.simpleremote"
    compileSdk = 36

    // Upload key for Google Play, kept outside the repo. Point -PkeystoreProps at another file to override.
    val keystoreProps = Properties().apply {
        val f = file(providers.gradleProperty("keystoreProps").orNull
            ?: "${System.getProperty("user.home")}/keys/simpleremote-keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("upload") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    defaultConfig {
        // Store identity; the Kotlin package (namespace) stays com.simpleremote.
        applicationId = "com.coriumcake.simpleremote"
        // ConsumerIrManager exists since API 19; 4.4.3+ uses microsecond patterns.
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Opt-in anonymous code statistics endpoint (see server/ and data/Stats.kt). Empty = never sent.
        buildConfigField("String", "STATS_URL", "\"${providers.gradleProperty("statsUrl").orNull ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("upload")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // IRext AC decoder (C, MIT) — generates the IR frame for any AC state from a ~300 byte protocol file.
    // Build without it via `-Pirext=false` (the app then only offers SmartIR remotes).
    if (providers.gradleProperty("irext").orNull != "false") {
        ndkVersion = "28.2.13676358"
        externalNativeBuild {
            ndkBuild { path = file("src/main/cpp/Android.mk") }
        }
        defaultConfig {
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}
