plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.walkieclaudie.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.walkieclaudie.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
    // QR scanning via Google Play services: no camera permission, no bundled scanner.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
