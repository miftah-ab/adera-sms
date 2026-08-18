plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)      // Required for Kotlin 2.x Compose compiler plugin
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

android {
    namespace  = "com.adera.sms"
    compileSdk = 36    // Android 16 — latest stable release

    defaultConfig {
        applicationId  = "com.adera.sms"
        minSdk         = 26   // Android 8.0 Oreo — covers Tecno/Infinix budget devices in Ethiopia
        targetSdk      = 36   // Android 16 — latest stable release
        versionCode    = 4    // INCREMENT this for every release; used by forced-update mechanism (spec 12.6)
        versionName    = "1.0.3"

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

    lint {
        // Disable the NullSafeMutableLiveData lint rule — it crashes with
        // IncompatibleClassChangeError on Kotlin 2.1.0 because the lifecycle-lint
        // detector (NonNullableMutableLiveDataDetector) was compiled against an older
        // Kotlin Analysis API where KaCallableMemberCall was a class; in Kotlin 2.x
        // it is an interface. This is a known upstream bug in lifecycle-lint.
        // Re-enable once a compatible lifecycle-lint version is released.
        disable += "NullSafeMutableLiveData"
        // Treat deprecation warnings as warnings only — never fail the build on warnings
        warningsAsErrors = false
        abortOnError = false
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
        // With Kotlin 2.x, the Compose compiler is managed by the kotlin.compose plugin.
        // This block is kept for backward compatibility with older toolchains.
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


    // Firebase (BOM pins all SDK versions consistently)
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-inappmessaging-display")  // Item 5: contextual Pro upgrade prompts
    implementation("com.google.firebase:firebase-messaging")               // Item 6: push notification support
    implementation("com.google.firebase:firebase-config")                  // Item 7: remote-adjustable values (daily cap, template limit)
    // Item 8: A/B Testing is built into Remote Config — available from the
    // Firebase console once firebase-config is integrated. No additional SDK needed.
}
