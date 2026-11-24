plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.terrasync.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.terrasync.app"
        minSdk = 24
        targetSdk = 34
        val tagVer = (project.findProperty("appVersionName") as String?)
            ?: System.getenv("GITHUB_REF_NAME")?.removePrefix("v")?.takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) }
            ?: "2.6.0"
        val computedCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
            ?: tagVer.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
                .let { if (it.size >= 3) it[0] * 10000 + it[1] * 100 + it[2] else 20600 }
        versionCode = computedCode
        versionName = tagVer
    }

    signingConfigs {
        create("release") {
            storeFile = file("terrasync-release.p12")
            storeType = "PKCS12"
            storePassword = "TerraSync2026"
            keyAlias = "terrasync"
            keyPassword = "TerraSync2026"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.maplibre.gl:android-sdk:11.13.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.car.app:app:1.4.0")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore")
}
