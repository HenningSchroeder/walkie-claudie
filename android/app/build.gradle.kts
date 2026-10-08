import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        // Shown in the app so a screenshot tells which build is installed.
        fun git(vararg a: String) = providers.exec { commandLine("git", *a) }.standardOutput.asText.get().trim()
        val dirty = if (git("status", "--porcelain").isEmpty()) "" else "+"
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        buildConfigField("String", "BUILD", "\"${git("rev-parse", "--short", "HEAD")}$dirty $time\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // QR scanning via Google Play services: no camera permission, no bundled scanner.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
