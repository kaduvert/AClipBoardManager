plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// -----------------------------------------------------------------------
// Capture-path compile flags
// -----------------------------------------------------------------------
// ClipVault used to let you flip between LSPosed and a root/priv-app capture
// service with an in-app Settings switch, with both code paths always
// compiled in. That's gone. There is now exactly one capture path per
// build, chosen here, at compile time - not at runtime:
//
//   ./gradlew assembleRelease                        -> LSPosed build (default)
//   ./gradlew assembleRelease -Pclipvault.useRoot=true -> root/priv-app build
//
// clipvault.useRoot=false (default): builds app/src/xposed/ into the app.
// No root/priv-app code (app/src/root/), no foreground service, no boot
// receiver, and no READ_CLIPBOARD_IN_BACKGROUND permission are compiled in
// or declared - there's nothing in this APK a root module would ever need
// to touch.
//
// clipvault.useRoot=true: builds app/src/root/ into the app instead. No
// Xposed/LSPosed code (app/src/xposed/), no ClipboardProvider IPC bridge,
// no xposedmodule manifest metadata, and neither Xposed API dependency are
// compiled in or resolved. This build isn't meant to be installed normally
// - see /module, which flashes it straight into /system/priv-app.
//
// clipvault.modernXposedHook keeps its pre-existing meaning and only
// matters inside the LSPosed build: whether the modern libxposed-API entry
// point's manifest (META-INF/xposed/*) is shipped alongside the classic
// rovo89-style one, which - as before - is always shipped for maximum
// framework compatibility.
val useRoot = (project.findProperty("clipvault.useRoot") as String?)
    ?.toBoolean() ?: false
val enableModernXposedHook = (project.findProperty("clipvault.modernXposedHook") as String?)
    ?.toBoolean() ?: true

val captureSourceSet = if (useRoot) "root" else "xposed"

android {
    namespace = "com.clipvault.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clipvault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.3"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("my_release_key.jks")
            
            if (keystoreFile.exists()) {
                storeFile = file("my_release_key.jks")
                storePassword = "000000"
                keyAlias = "clipvault"
                keyPassword = "000000"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                if (useRoot) "proguard-rules-root.pro" else "proguard-rules-xposed.pro"
            )
            val releaseConfig = signingConfigs.getByName("release")
            
            if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists()) {
                // We have the key, use the release signature
                signingConfig = releaseConfig
            } else {
                // We are in CI and the key is missing, fallback to debug signature
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The actual "compile flag" mechanism: main's manifest and its java/res/
    // assets/resources source dirs are pointed at app/src/xposed or
    // app/src/root depending on useRoot, ON TOP OF the common code that
    // always lives in app/src/main. Each of the two only ever has ONE of
    // them added, so only one capture path's classes, manifest entries,
    // and (for xposed) Xposed-module resources ever reach the compiler or
    // the APK - this is real exclusion, not a packaging-time filter.
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/$captureSourceSet/AndroidManifest.xml")
            java.srcDirs("src/$captureSourceSet/java")
            kotlin.srcDir("src/$captureSourceSet/java")
            res.srcDirs("src/$captureSourceSet/res")
        
            if (!useRoot) {
                assets.srcDirs("src/xposed/assets")
                resources.srcDirs("src/xposed/resources")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Only reachable when useRoot is false: app/src/xposed/resources
            // (and therefore META-INF/xposed/**) isn't even a source dir of
            // this variant when useRoot is true, so there's nothing for
            // this exclude to do in a root build - it's a no-op there.
            if (!useRoot && !enableModernXposedHook) {
                excludes += "META-INF/xposed/**"
            }
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coroutines (also pulled in transitively, declared explicitly for clarity)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Room 3.0 - new package/artifact group (androidx.room3), KSP-only, no more
    // Java codegen. Needs an explicit SQLiteDriver since it no longer touches
    // Android's SupportSQLite types at all (see ClipDatabase.kt).
    implementation("androidx.room3:room3-runtime:3.0.0")
    implementation("androidx.sqlite:sqlite-framework:2.7.0")
    ksp("androidx.room3:room3-compiler:3.0.0")

    // DataStore (settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Xposed API surfaces - compileOnly (provided by the LSPosed/Vector
    // framework at runtime, never bundled into the APK either way) and now
    // also only even resolved for the LSPosed build in the first place.
    // A root build touches neither: it never references any Xposed type,
    // so there's nothing here for it to depend on.
    if (!useRoot) {
        compileOnly("com.github.deltazefiro:XposedBridge:3137dcc")
        compileOnly("io.github.libxposed:api:102.0.0")
    }

    // No su-shell / libsu dependency any more, in either build. The old
    // in-app "Use root capture service" switch used it to (a) decide
    // whether to let you flip that switch on and (b) let the app try to
    // promote itself. Both are gone: a root build's only privilege comes
    // from already being flashed in as a priv-app by /module before the
    // app process ever runs (see capture/CaptureCoordinator.kt under
    // app/src/root), so nothing in the running app ever needs to shell out
    // to su, and there's no runtime decision left to gate on "is su available".
}
