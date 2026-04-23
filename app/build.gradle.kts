plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hapticks.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hapticks.app"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    dependenciesInfo {
        // Strip signed dependency metadata from APKs so the package is smaller when
        // sideloaded. Play Store builds keep it via the bundle.
        includeInApk = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    if (!rootProject.file("app/libs/api-82.jar").exists()) {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            exclude("**/com/hapticks/app/edge/EdgeScrollHooks.kt")
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Xposed API for LSPosed module. Bundled as a local JAR because the traditional
    // JCenter coordinates no longer resolve. The JAR is compileOnly — never packaged
    // into the APK — so at runtime we rely on LSPosed having injected the real
    // classes. When the JAR is absent the edge/EdgeScrollHooks.kt entry point is
    // excluded from the build (see sourceSets above) and EdgeHapticsBridge reports
    // the feature as unavailable at runtime.
    if (rootProject.file("app/libs/api-82.jar").exists()) {
        compileOnly(files("libs/api-82.jar"))
    }

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
