plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

android {
    namespace  = "com.adera.sms"
    compileSdk = 34

    defaultConfig {
        applicationId  = "com.adera.sms"
        minSdk         = 26   // Android 8.0 Oreo — covers Tecno/Infinix budget devices in Ethiopia
        targetSdk      = 34   // Android 14
        versionCode    = 3    // INCREMENT this for every release; used by forced-update mechanism (spec 12.6)
        versionName    = "1.0.2"

        // Export Room schema for migration history tracking
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
    }

    // -----------------------------------------------------------------------
    // Signing — release APK must be signed consistently across versions
    // (spec 3.5: "APK signed consistently across versions so updates don't
    //  break trust/reinstall").
    //
    // GitHub Actions: the keystore is decoded from SIGNING_KEY_BASE64 secret
    // and written to <project-root>/keystore.jks before assembleRelease runs.
    //
    // Local setup: generate a keystore once and store it securely outside the repo.
    //   keytool -genkey -v -keystore keystore.jks -alias adera -keyalg RSA \
    //           -keysize 2048 -validity 10000
    //   Then base64-encode it: base64 keystore.jks | pbcopy (macOS) or
    //   certutil -encode keystore.jks keystore.b64 (Windows)
    //   Add the base64 output as the SIGNING_KEY_BASE64 GitHub Actions secret.
    // -----------------------------------------------------------------------
    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore.jks")
            if (keystoreFile.exists()) {
                storeFile     = keystoreFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
                keyAlias      = System.getenv("SIGNING_KEY_ALIAS") ?: ""
                keyPassword   = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
            }
            // If keystore.jks doesn't exist (e.g. a PR build), release APK
            // will be unsigned — GitHub Actions only signs on tag pushes.
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix  = ".debug"
            versionNameSuffix    = "-debug"
            isDebuggable         = true
            // Debug builds are not minified — faster iteration, readable stack traces
        }
        release {
            isMinifyEnabled      = true
            isShrinkResources    = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val relCfg = signingConfigs.findByName("release")
            if (relCfg?.storeFile?.exists() == true) {
                signingConfig = relCfg
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose     = true
        buildConfig = true   // Needed for BuildConfig.VERSION_CODE in update checker (Step 10)
    }
    composeOptions {
        // Must align with Kotlin version — see libs.versions.toml for the version matrix
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)  // collectAsStateWithLifecycle + LocalLifecycleOwner
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (BOM manages individual library versions)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Room — fully local SQLite, zero network dependency (spec 7: no backend for v1)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager — background SMS send + single retry (spec 12.3 + 12.7)
    implementation(libs.workmanager.ktx)

    // Navigation — Compose NavHost for all screens
    implementation(libs.navigation.compose)

    // ViewModel for Compose screens
    implementation(libs.lifecycle.viewmodel.compose)

    // Coroutines — service scope, DAO suspend functions, worker async
    implementation(libs.kotlinx.coroutines.android)

    // Browser — Chrome Custom Tabs for Ye Buna payment link
    implementation(libs.androidx.browser)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
}
