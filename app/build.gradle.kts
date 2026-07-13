plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val enableModernXposedHook = (project.findProperty("clipvault.modernXposedHook") as String?)
    ?.toBoolean() ?: false

android {
    namespace = "com.clipvault.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clipvault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("my_release_key.jks")
            storePassword = "000000"
            keyAlias = "clipvault"
            keyPassword = "000000"
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
            signingConfig = signingConfigs.getByName("release")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            if (!enableModernXposedHook) {
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

    // Root detection / shell (used only for Settings status + Magisk-assisted install helper)
    implementation("com.github.topjohnwu.libsu:core:5.2.2")

    // Classic Xposed API - compileOnly, provided by the LSPosed/Vector framework at runtime
    compileOnly("com.github.deltazefiro:XposedBridge:3137dcc")

    // Modern libxposed API - compileOnly, provided by frameworks that support it at runtime.
    // See ClipboardHookModern's doc comment for why 102 specifically.
    compileOnly("io.github.libxposed:api:102.0.0")
}
