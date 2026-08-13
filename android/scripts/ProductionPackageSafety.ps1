# 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt134 production package safety helpers
$ErrorActionPreference = 'Stop'

$Script:AllowedDeviceTestPackage = 'com.me4hik.praktika.accelerated'
$Script:ProductionPackageId = 'com.me4hik.praktika'
$Script:ProductionDeployWorkflow = $false

$Script:BlockedGradleDeviceTasks = @(
    'connectedDebugAndroidTest'
    'connectedProductionDebugAndroidTest'
    'connectedProductionReleaseAndroidTest'
    'installProductionDebug'
    'installProductionDebugAndroidTest'
    'uninstallProductionRelease'
    'uninstallAll'
)

$Script:SafeGradleDeviceTask = 'connectedAcceleratedDebugAndroidTest'

$Script:ForbiddenProductionMutations = @(
    'adb uninstall com.me4hik.praktika'
    'pm uninstall com.me4hik.praktika'
    'pm clear com.me4hik.praktika'
    'connectedProductionDebugAndroidTest'
    'connectedDebugAndroidTest'
    'installProductionDebug'
)

function Initialize-DeviceTestSafety {
    $Script:ProductionDeployWorkflow = $false
}

function Initialize-ProductionDeploySafety {
    $Script:ProductionDeployWorkflow = $true
}

function Test-ForbiddenProductionPackageReference {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $false
    }
    if ($Text -notmatch 'com\.me4hik\.praktika') {
        return $false
    }
    if ($Text -match 'com\.me4hik\.praktika\.accelerated') {
        return $false
    }
    return $true
}

function Assert-AcceleratedOnlyPackage {
    param([string]$PackageId)

    if ($Script:ProductionDeployWorkflow) {
        return
    }
    if ($PackageId -eq $Script:AllowedDeviceTestPackage) {
        return
    }
    if (Test-ForbiddenProductionPackageReference $PackageId) {
        throw @"
PRODUCTION_DEVICE_TEST_BLOCKED: device-test script target must be accelerated only.
Target: $PackageId
Allowed: $Script:AllowedDeviceTestPackage
"@
    }
}

function Assert-NoProductionPackageMutation {
    param([string]$CommandText)

    if ($Script:ProductionDeployWorkflow) {
        return
    }
    foreach ($forbidden in $Script:ForbiddenProductionMutations) {
        if ($CommandText -like "*$forbidden*") {
            throw "PRODUCTION_PACKAGE_MUTATION_BLOCKED: $forbidden"
        }
    }
    if (Test-ForbiddenProductionPackageReference $CommandText) {
        throw @"
PRODUCTION_DEVICE_TEST_BLOCKED: command references production package.
Command: $CommandText
Use $Script:AllowedDeviceTestPackage or an explicit production deploy workflow script.
"@
    }
}

function Assert-SafeGradleDeviceTestCommand {
    param([string[]]$GradleArgs)

    if ($Script:ProductionDeployWorkflow) {
        return
    }

    $joined = ($GradleArgs -join ' ')
    Assert-NoProductionPackageMutation $joined

    foreach ($blocked in $Script:BlockedGradleDeviceTasks) {
        if ($joined -match [regex]::Escape($blocked)) {
            throw @"
PRODUCTION_DEVICE_TEST_BLOCKED: Gradle task '$blocked' is forbidden in device-test scripts.
Safe task: :app:$Script:SafeGradleDeviceTask
Override (Gradle only): -PALLOW_PRODUCTION_DEVICE_TESTS=true
"@
        }
    }

    if ($joined -match 'connectedProduction\w*AndroidTest') {
        throw 'PRODUCTION_DEVICE_TEST_BLOCKED: connectedProduction*AndroidTest is forbidden.'
    }
}

function Assert-ProductionReleaseDeployCommand {
    param(
        [string]$ApkPath,
        [switch]$AllowDebugApk
    )

    if (-not $Script:ProductionDeployWorkflow) {
        throw 'PRODUCTION_DEPLOY_MISUSE: release deploy helper called outside deploy workflow.'
    }
    if (-not $AllowDebugApk -and $ApkPath -match 'production[/\\]debug') {
        throw @"
PRODUCTION_DEPLOY_BLOCKED: production deploy must use signed release APK, not productionDebug.
Path: $ApkPath
"@
    }
}
# 10.08.2026 Post-release fixes cursor by Me4Hik END
