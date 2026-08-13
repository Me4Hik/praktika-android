// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt134 production device test hard guard
import java.util.Properties

val productionPackageId = "com.me4hik.praktika"
val allowProductionDeviceTests = providers.gradleProperty("ALLOW_PRODUCTION_DEVICE_TESTS")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)

fun isBlockedProductionDeviceTask(taskName: String): Boolean {
    if (taskName == "connectedDebugAndroidTest") {
        return true
    }
    if (taskName.startsWith("connectedProduction") && taskName.endsWith("AndroidTest")) {
        return true
    }
    if (taskName == "installProductionDebug") {
        return true
    }
    if (taskName == "installProductionDebugAndroidTest") {
        return true
    }
    if (taskName == "uninstallProductionRelease") {
        return true
    }
    if (taskName == "uninstallAll") {
        return true
    }
    return false
}

fun findAdbExecutable(): java.io.File? {
    val localProperties = rootProject.file("local.properties")
    if (!localProperties.isFile) {
        return null
    }
    val props = Properties()
    localProperties.inputStream().use { props.load(it) }
    val sdkDir = props.getProperty("sdk.dir")?.trim().orEmpty()
    if (sdkDir.isEmpty()) {
        return null
    }
    val adbName = if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
        "adb.exe"
    } else {
        "adb"
    }
    val adb = java.io.File(sdkDir, "platform-tools/$adbName")
    return adb.takeIf { it.isFile }
}

fun assertProductionReleaseNotInstalledForDebugTest() {
    val adb = findAdbExecutable()
        ?: throw GradleException(
            """
            PRODUCTION_DEVICE_TEST_PREFLIGHT_FAILED: adb not found (local.properties sdk.dir).
            Refusing production device test without release-package preflight.
            """.trimIndent(),
        )

    val pathProcess = ProcessBuilder(adb.absolutePath, "shell", "pm", "path", productionPackageId)
        .redirectErrorStream(true)
        .start()
    val packagePath = pathProcess.inputStream.bufferedReader().readText().trim()
    pathProcess.waitFor()
    if (!packagePath.startsWith("package:")) {
        return
    }

    val runAsProcess = ProcessBuilder(adb.absolutePath, "shell", "run-as", productionPackageId, "pwd")
        .redirectErrorStream(true)
        .start()
    val runAsOutput = runAsProcess.inputStream.bufferedReader().readText()
    runAsProcess.waitFor()
    if (runAsProcess.exitValue() == 0 && runAsOutput.isNotBlank()) {
        return
    }

    throw GradleException(
        """
        PRODUCTION_RELEASE_INSTALLED: Refusing production debug device test while signed release is on device.
        Package: $productionPackageId
        Safe command: :app:connectedAcceleratedDebugAndroidTest
        Release deployment must use signed release APK (adb install -r), not productionDebug connected tests.
        """.trimIndent(),
    )
}

gradle.taskGraph.whenReady {
    val blockedTasks = allTasks.filter { isBlockedProductionDeviceTask(it.name) }
    if (blockedTasks.isEmpty()) {
        return@whenReady
    }

    if (!allowProductionDeviceTests) {
        throw GradleException(
            """
            PRODUCTION_DEVICE_TEST_BLOCKED: Use accelerated variant for device tests.
            Blocked task(s): ${blockedTasks.joinToString { it.path }}
            Safe command: :app:connectedAcceleratedDebugAndroidTest
            Override (rare): -PALLOW_PRODUCTION_DEVICE_TESTS=true
            """.trimIndent(),
        )
    }

    assertProductionReleaseNotInstalledForDebugTest()
}

// AGP with product flavors has no single umbrella task; block the abbreviated footgun explicitly.
tasks.register("connectedDebugAndroidTest") {
    group = "verification"
    description = "Blocked — use :app:connectedAcceleratedDebugAndroidTest (accelerated only)."
    doFirst {
        if (!allowProductionDeviceTests) {
            throw GradleException(
                """
                PRODUCTION_DEVICE_TEST_BLOCKED: Use accelerated variant for device tests.
                Blocked task: connectedDebugAndroidTest (umbrella footgun).
                Safe command: :app:connectedAcceleratedDebugAndroidTest
                Override (rare): -PALLOW_PRODUCTION_DEVICE_TESTS=true
                """.trimIndent(),
            )
        }
        assertProductionReleaseNotInstalledForDebugTest()
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
