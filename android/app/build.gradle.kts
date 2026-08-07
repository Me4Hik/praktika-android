// 04.08.2026 Reminder App cursor by Me4Hik START - модуль app каркаса Практика
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // 04.08.2026 DB Refactoring cursor by Me4Hik START - KSP и Room Gradle Plugin
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // 04.08.2026 DB Refactoring cursor by Me4Hik END
}

// 04.08.2026 DB Refactoring cursor by Me4Hik START - каталог экспорта Room schema
room {
    schemaDirectory("$projectDir/schemas")
}
// 04.08.2026 DB Refactoring cursor by Me4Hik END

// 07.08.2026 Stage 26 Release cursor by Me4Hik START - production signing from external secrets
val releaseSecretsFile = file("local-release-secrets/release-secrets.properties")
val releaseSecrets = Properties().apply {
    if (releaseSecretsFile.exists()) {
        releaseSecretsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.me4hik.praktika"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.me4hik.praktika"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - product flavors production/accelerated
    flavorDimensions += "runtimeMode"

    productFlavors {
        create("production") {
            dimension = "runtimeMode"
        }
        create("accelerated") {
            dimension = "runtimeMode"
            applicationIdSuffix = ".accelerated"
            versionNameSuffix = "-accelerated"
        }
    }
    // 04.08.2026 Accelerated Test Mode cursor by Me4Hik END

    // 07.08.2026 Stage 26 Release cursor by Me4Hik START - permanent production release signing
    signingConfigs {
        create("release") {
            val storePath = releaseSecrets.getProperty("storeFile")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
            }
            storePassword = releaseSecrets.getProperty("storePassword")
            keyAlias = releaseSecrets.getProperty("keyAlias")
            keyPassword = releaseSecrets.getProperty("keyPassword")
        }
    }
    // 07.08.2026 Stage 26 Release cursor by Me4Hik END

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
        // 04.08.2026 Cycle Engine cursor by Me4Hik START - java.time через core library desugaring
        isCoreLibraryDesugaringEnabled = true
        // 04.08.2026 Cycle Engine cursor by Me4Hik END
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 05.08.2026 Main Screen cursor by Me4Hik START - stub Android Log в JVM unit tests
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    // 05.08.2026 Main Screen cursor by Me4Hik END
}

// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - отключить acceleratedRelease
androidComponents {
    beforeVariants(
        selector()
            .withBuildType("release")
            .withFlavor("runtimeMode", "accelerated"),
    ) { variantBuilder ->
        variantBuilder.enable = false
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // 05.08.2026 Main Screen cursor by Me4Hik START - ViewModel и collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // 05.08.2026 Main Screen cursor by Me4Hik END
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // 04.08.2026 DB Refactoring cursor by Me4Hik START - Room runtime и KSP compiler
    implementation(libs.androidx.room.runtime)
    // 05.08.2026 Atomic Practice Snapshot cursor by Me4Hik START - withTransaction для read snapshot
    implementation(libs.androidx.room.ktx)
    // 05.08.2026 Atomic Practice Snapshot cursor by Me4Hik END
    ksp(libs.androidx.room.compiler)
    // 04.08.2026 Seed Data cursor by Me4Hik START - coroutines для DatabaseSeeder и MainActivity
    implementation(libs.kotlinx.coroutines.android)
    // 06.08.2026 Settings Schedule cursor by Me4Hik START - Preferences DataStore для звука
    implementation(libs.androidx.datastore.preferences)
    // 06.08.2026 Settings Schedule cursor by Me4Hik END
    // 04.08.2026 DB Refactoring cursor by Me4Hik END

    // 04.08.2026 Cycle Engine cursor by Me4Hik START - coreLibraryDesugaring для java.time на minSdk 24
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // 04.08.2026 Cycle Engine cursor by Me4Hik END

    testImplementation(libs.junit)
    // 05.08.2026 Main Screen cursor by Me4Hik START - coroutines test для ViewModel
    testImplementation(libs.kotlinx.coroutines.test)
    // 05.08.2026 Main Screen cursor by Me4Hik END
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // 04.08.2026 DB Refactoring cursor by Me4Hik START - Room testing и AndroidX Test
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(libs.kotlinx.coroutines.android)
    // 04.08.2026 DB Refactoring cursor by Me4Hik END
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
// 04.08.2026 Reminder App cursor by Me4Hik END
