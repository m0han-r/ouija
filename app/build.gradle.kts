plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val exactCommitTag: String? = try {
    providers.exec {
        commandLine("git", "describe", "--tags", "--exact-match", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }.get().ifBlank { null }
} catch (_: Exception) {
    null
}

val commitHash6: String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short=6", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().take(6) }.get().ifBlank { "dev000" }
} catch (_: Exception) {
    "dev000"
}

// If current commit has a tag, use it (e.g. v1.2.0); else use that commit's 6-character hashcode
val versionIdentifier: String = exactCommitTag ?: commitHash6
val appVersionName: String = versionIdentifier.removePrefix("v")

base {
    archivesName.set("ouija-$versionIdentifier")
}

android {
    namespace = "ouija.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ouija.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                if (variant.name == "release") {
                    "ouija-${versionIdentifier}.apk"
                } else {
                    "ouija-${versionIdentifier}-${variant.name}.apk"
                }
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Supabase & Ktor & Serialization
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Media3 & DataStore
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
