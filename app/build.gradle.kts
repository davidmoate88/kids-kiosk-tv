import java.net.URI
import java.net.URISyntaxException

plugins {
    id("com.android.application")
}

fun validatedKioskUrl(propertyName: String, value: String, httpsOnly: Boolean): String {
    val uri = try {
        URI(value)
    } catch (error: URISyntaxException) {
        throw GradleException("$propertyName must be a valid URL", error)
    }
    require(uri.host != null && uri.userInfo == null) {
        "$propertyName must include a host and must not include credentials"
    }
    require(uri.scheme.equals("http", ignoreCase = true)
            || uri.scheme.equals("https", ignoreCase = true)) {
        "$propertyName must use HTTP or HTTPS"
    }
    require(!httpsOnly || uri.scheme.equals("https", ignoreCase = true)) {
        "$propertyName must use HTTPS"
    }
    return value
}

val debugKioskUrl = validatedKioskUrl(
    "debugKioskUrl",
    providers.gradleProperty("debugKioskUrl").orElse("http://192.168.1.58/tv").get(),
    httpsOnly = false,
)
val releaseKioskUrl = validatedKioskUrl(
    "releaseKioskUrl",
    providers.gradleProperty("releaseKioskUrl").orElse("https://kids-kiosk.vercel.app/tv").get(),
    httpsOnly = true,
)

android {
    namespace = "com.kidskiosk.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kidskiosk.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        debug {
            buildConfigField("String", "KIOSK_URL", "\"$debugKioskUrl\"")
        }
        release {
            buildConfigField("String", "KIOSK_URL", "\"$releaseKioskUrl\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.webkit:webkit:1.16.0")

    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
