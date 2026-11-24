import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val notificationSoundSourceDir = layout.projectDirectory.dir("notification-sounds")
val notificationSoundGeneratedResDir = layout.buildDirectory.dir("generated/res/koraxNotificationSounds")
val generateNotificationSounds by tasks.registering {
    inputs.dir(notificationSoundSourceDir)
    outputs.dir(notificationSoundGeneratedResDir)
    doLast {
        val rawDirectory = notificationSoundGeneratedResDir.get().asFile.resolve("raw")
        rawDirectory.mkdirs()
        notificationSoundSourceDir.asFile.listFiles { file -> file.isFile && file.name.endsWith(".wav.b64") }
            ?.forEach { source ->
                val resourceName = source.name.removeSuffix(".wav.b64").lowercase()
                val decoded = Base64.getDecoder().decode(source.readText().trim())
                rawDirectory.resolve("${resourceName}_v3.wav").writeBytes(decoded)
            }
    }
}

android {
    namespace = "com.koora.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.koora.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.6.9"

        val apiBase = providers.gradleProperty("KOORA_API_BASE_URL")
            .orElse("https://hwrsjmdcivdluztnhpxo.supabase.co/functions/v1/korax-api").get()
        buildConfigField("String", "KOORA_API_BASE_URL", "\"$apiBase\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_qGcLhGeCxVU5MvFVQ2YlPQ_XKGS42QW\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

android.sourceSets.getByName("main").res.srcDir(notificationSoundGeneratedResDir)
tasks.named("preBuild").configure {
    dependsOn(generateNotificationSounds)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
