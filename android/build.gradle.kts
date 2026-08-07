// 04.08.2026 Reminder App cursor by Me4Hik START - корневой build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // 04.08.2026 DB Refactoring cursor by Me4Hik START - KSP и Room plugin apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    // 04.08.2026 DB Refactoring cursor by Me4Hik END
}

// 04.08.2026 DB Refactoring cursor by Me4Hik START - фикс Room schema export и kotlinx-serialization
subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (
                requested.group == "org.jetbrains.kotlinx" &&
                requested.name.startsWith("kotlinx-serialization")
            ) {
                useVersion("1.8.1")
            }
        }
    }
}
// 04.08.2026 DB Refactoring cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
