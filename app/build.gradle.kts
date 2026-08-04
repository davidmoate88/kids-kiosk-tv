plugins {
    id("com.android.application")
}

android {
    namespace = "com.kidskiosk.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kidskiosk.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}
