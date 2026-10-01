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

// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val sentryDsn = localProperties.getProperty("sentry.dsn").orEmpty()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
// 10.08.2026 Post-release fixes cursor by Me4Hik END

// 07.08.2026 Stage 26 Release cursor by Me4Hik START - production signing from external secrets
// 14.08.2026 DB Refactoring cursor by Me4Hik START - make release signing path machine-independent
val releaseSecretsPath = localProperties.getProperty("release.secrets.file").orEmpty().trim()
val releaseSecretsFile = if (releaseSecretsPath.isBlank()) {
    null
} else {
    rootProject.file(releaseSecretsPath)
}
val releaseSecrets = Properties().apply {
    val secretsFile = releaseSecretsFile
    if (secretsFile != null && secretsFile.exists()) {
        secretsFile.inputStream().use { load(it) }
    }
}
// 14.08.2026 DB Refactoring cursor by Me4Hik END
// 07.08.2026 Stage 26 Release cursor by Me4Hik END

android {
    namespace = "com.me4hik.praktika"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.me4hik.praktika"
        minSdk = 24
        targetSdk = 36
        versionCode = 17
        versionName = "1.0"

        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
        // 10.08.2026 Post-release fixes cursor by Me4Hik END

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
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.2 Compose Robolectric host tests
        unitTests.isIncludeAndroidResources = true
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
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

// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt134 production device test hard guard
apply(from = rootProject.file("gradle/production-device-test-guard.gradle.kts"))
// 10.08.2026 Post-release fixes cursor by Me4Hik END

// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt131 device test artifact path sanitization
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    doLast {
        sanitizeConnectedAndroidTestArtifacts(name)
    }
}

fun sanitizeConnectedAndroidTestArtifacts(taskName: String) {
    val flavorSegment = when {
        taskName.contains("Accelerated", ignoreCase = true) -> "accelerated"
        else -> "production"
    }
    val buildTypeSegment = if (taskName.contains("Debug", ignoreCase = true)) "debug" else "release"
    val resultRoots = listOf(
        layout.buildDirectory
            .dir("outputs/androidTest-results/connected/$buildTypeSegment/flavors/$flavorSegment")
            .get()
            .asFile,
        layout.buildDirectory
            .dir("reports/androidTests/connected/$buildTypeSegment/flavors/$flavorSegment")
            .get()
            .asFile,
    )
    resultRoots.forEach { root ->
        if (!root.exists()) {
            return@forEach
        }
        root.listFiles()?.filter { it.isDirectory }?.forEach { deviceDir ->
            val safeName = deviceDir.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
            val resolvedDir = if (safeName != deviceDir.name) {
                val target = deviceDir.parentFile.resolve(safeName)
                if (!target.exists()) {
                    deviceDir.renameTo(target)
                }
                target
            } else {
                deviceDir
            }
            resolvedDir.walkTopDown().maxDepth(2).filter { it.isFile && it.name.startsWith("logcat-") }.forEach { logcat ->
                if (!logcat.exists() || logcat.length() == 0L) {
                    logcat.parentFile?.mkdirs()
                    logcat.writeText("")
                }
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

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

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
    implementation(libs.sentry.android)
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    // 04.08.2026 Cycle Engine cursor by Me4Hik START - coreLibraryDesugaring для java.time на minSdk 24
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // 04.08.2026 Cycle Engine cursor by Me4Hik END

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    // 05.08.2026 Main Screen cursor by Me4Hik START - coroutines test для ViewModel
    testImplementation(libs.kotlinx.coroutines.test)
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 cancellation JVM tests
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.androidx.activity.compose)
    testImplementation(libs.androidx.material3)
    testImplementation(libs.androidx.ui)
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
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
