# 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt134 production package safety helpers
"""Reusable guards for device-test and production deploy scripts."""

from __future__ import annotations

ALLOWED_DEVICE_TEST_PACKAGE = "com.me4hik.praktika.accelerated"
PRODUCTION_PACKAGE_ID = "com.me4hik.praktika"
SAFE_GRADLE_DEVICE_TASK = "connectedAcceleratedDebugAndroidTest"

BLOCKED_GRADLE_DEVICE_TASKS = frozenset(
    {
        "connectedDebugAndroidTest",
        "connectedProductionDebugAndroidTest",
        "connectedProductionReleaseAndroidTest",
        "installProductionDebug",
        "installProductionDebugAndroidTest",
        "uninstallProductionRelease",
        "uninstallAll",
    }
)

FORBIDDEN_PRODUCTION_MUTATIONS = frozenset(
    {
        "adb uninstall com.me4hik.praktika",
        "pm uninstall com.me4hik.praktika",
        "pm clear com.me4hik.praktika",
        "connectedProductionDebugAndroidTest",
        "connectedDebugAndroidTest",
        "installProductionDebug",
    }
)


class ProductionPackageSafetyError(RuntimeError):
    pass


def _contains_forbidden_production_reference(text: str) -> bool:
    if "com.me4hik.praktika" not in text:
        return False
    if "com.me4hik.praktika.accelerated" in text:
        return False
    return True


def assert_accelerated_only_package(package_id: str, *, production_deploy: bool = False) -> None:
    if production_deploy:
        return
    if package_id == ALLOWED_DEVICE_TEST_PACKAGE:
        return
    if _contains_forbidden_production_reference(package_id):
        raise ProductionPackageSafetyError(
            "PRODUCTION_DEVICE_TEST_BLOCKED: device-test script target must be accelerated only. "
            f"Target: {package_id}. Allowed: {ALLOWED_DEVICE_TEST_PACKAGE}."
        )


def assert_no_production_package_mutation(command_text: str, *, production_deploy: bool = False) -> None:
    if production_deploy:
        return
    for forbidden in FORBIDDEN_PRODUCTION_MUTATIONS:
        if forbidden in command_text:
            raise ProductionPackageSafetyError(f"PRODUCTION_PACKAGE_MUTATION_BLOCKED: {forbidden}")
    if _contains_forbidden_production_reference(command_text):
        raise ProductionPackageSafetyError(
            "PRODUCTION_DEVICE_TEST_BLOCKED: command references production package. "
            f"Command: {command_text}. Use {ALLOWED_DEVICE_TEST_PACKAGE} "
            "or an explicit production deploy workflow script."
        )


def assert_safe_gradle_device_test_command(gradle_args: list[str], *, production_deploy: bool = False) -> None:
    if production_deploy:
        return
    joined = " ".join(gradle_args)
    assert_no_production_package_mutation(joined, production_deploy=production_deploy)
    for blocked in BLOCKED_GRADLE_DEVICE_TASKS:
        if blocked in joined:
            raise ProductionPackageSafetyError(
                "PRODUCTION_DEVICE_TEST_BLOCKED: "
                f"Gradle task '{blocked}' is forbidden in device-test scripts. "
                f"Safe task: :app:{SAFE_GRADLE_DEVICE_TASK}. "
                "Override (Gradle only): -PALLOW_PRODUCTION_DEVICE_TESTS=true."
            )
    if "connectedProduction" in joined and "AndroidTest" in joined:
        raise ProductionPackageSafetyError(
            "PRODUCTION_DEVICE_TEST_BLOCKED: connectedProduction*AndroidTest is forbidden."
        )


def assert_production_release_deploy(apk_path: str, *, production_deploy: bool) -> None:
    if not production_deploy:
        raise ProductionPackageSafetyError(
            "PRODUCTION_DEPLOY_MISUSE: release deploy helper called outside deploy workflow."
        )
    normalized = apk_path.replace("\\", "/")
    if "/production/debug/" in normalized or normalized.endswith("app-production-debug.apk"):
        raise ProductionPackageSafetyError(
            "PRODUCTION_DEPLOY_BLOCKED: production deploy must use signed release APK, "
            f"not productionDebug. Path: {apk_path}."
        )
# 10.08.2026 Post-release fixes cursor by Me4Hik END
