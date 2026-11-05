plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.stasybell.os"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stasybell.os"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val devApiBase = providers.gradleProperty("stasyApiBaseUrl")
        .orElse("https://api.invalid/").get()
    val localDebug = devApiBase == "http://127.0.0.1:8000/" &&
        gradle.startParameter.taskNames.none { it.contains("Release", ignoreCase = true) }
    require((devApiBase.startsWith("https://") || localDebug) && devApiBase.endsWith("/")) {
        "API base must be HTTPS; only debug loopback is permitted as an HTTP build default"
    }
    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"$devApiBase\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    // Patch fixes for recomposition/slot-table invalidations in the 1.9 runtime.
    implementation("androidx.compose.runtime:runtime:1.9.5")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
