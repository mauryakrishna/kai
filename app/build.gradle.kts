plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.krishna.kai"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.krishna.kai"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-phase0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
