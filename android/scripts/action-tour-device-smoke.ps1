# ActionTour production black-box device smoke runner.
# Installs ONLY the dedicated smoke APK; never installs/replaces production.
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\android\scripts\action-tour-device-smoke.ps1
#   powershell -ExecutionPolicy Bypass -File .\android\scripts\action-tour-device-smoke.ps1 -Serial <serial> -SkipTimeoutRegression
#   powershell -ExecutionPolicy Bypass -File .\android\scripts\action-tour-device-smoke.ps1 -SanityOnly

[CmdletBinding()]
param(
    [string]$Serial = '',
    [switch]$SkipTimeoutRegression,
    [switch]$SanityOnly
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$AndroidRoot = Join-Path $RepoRoot 'android'
$ProductionPackage = 'com.me4hik.praktika'
$SmokeAppId = 'com.me4hik.praktika.actiontoursmoke'
$SmokeTestId = 'com.me4hik.praktika.actiontoursmoke.test'
$Runner = 'androidx.test.runner.AndroidJUnitRunner'
$MainClass = 'com.me4hik.praktika.actiontoursmoke.ActionTourDeviceSmokeTest'
$TimeoutClass = 'com.me4hik.praktika.actiontoursmoke.ActionTourTimeoutRegressionTest'

function New-AdbArgList {
    param([Parameter(Mandatory = $true)][string[]]$Items)
    return @($Items)
}

function Get-ParsedInstrumentVerdict {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$RawOutput,
        [int]$HostExit = 0
    )
    # INSTRUMENTATION_CODE -1 = Activity.RESULT_OK (not a test failure).
    # Per-test: INSTRUMENTATION_STATUS_CODE 0=OK, -1=error, -2=failure, 1=started.
    $result = [ordered]@{
        PARSED_VERDICT = 'FAIL'
        HOST_EXIT = $HostExit
        INSTRUMENTATION_CODE = $null
        HAS_JUNIT_OK = $false
        HAS_FAILURES_BANNER = $false
        FAILURE_STATUS_CODES = @()
        REASON = ''
        HOST_EXIT_DISAGREES = $false
    }
    if ([string]::IsNullOrWhiteSpace($RawOutput)) {
        $result.REASON = 'empty instrumentation output'
        return [pscustomobject]$result
    }
    $statusCodes = [regex]::Matches($RawOutput, 'INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)') |
        ForEach-Object { [int]$_.Groups[1].Value }
    $failStatuses = @($statusCodes | Where-Object { $_ -eq -1 -or $_ -eq -2 })
    $result.FAILURE_STATUS_CODES = $failStatuses
    $final = [regex]::Match($RawOutput, '(?m)^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$')
    if ($final.Success) { $result.INSTRUMENTATION_CODE = [int]$final.Groups[1].Value }
    $result.HAS_FAILURES_BANNER = $RawOutput -match 'FAILURES!!!'
    $result.HAS_JUNIT_OK = $RawOutput -match 'OK\s*\(\d+\s+tests?\)'
    if ($result.HAS_FAILURES_BANNER -or $failStatuses.Count -gt 0) {
        $result.PARSED_VERDICT = 'FAIL'
        $result.REASON = if ($result.HAS_FAILURES_BANNER) { 'FAILURES!!!' } else { "STATUS_CODE $($failStatuses -join ',')" }
    } elseif ($result.HAS_JUNIT_OK) {
        $result.PARSED_VERDICT = 'PASS'
        $result.REASON = "junit OK; INSTRUMENTATION_CODE=$($result.INSTRUMENTATION_CODE) is RESULT_OK/neutral"
    } else {
        $result.PARSED_VERDICT = 'FAIL'
        $result.REASON = 'missing OK (N tests) and no clear pass signal'
    }
    # Host exit non-zero with PARSED PASS (classic -1 CODE) = disagreement
    if ($result.PARSED_VERDICT -eq 'PASS' -and $HostExit -ne 0) {
        $result.HOST_EXIT_DISAGREES = $true
    }
    if ($result.PARSED_VERDICT -eq 'FAIL' -and $HostExit -eq 0 -and ($result.HAS_FAILURES_BANNER -or $failStatuses.Count -gt 0)) {
        $result.HOST_EXIT_DISAGREES = $true
    }
    return [pscustomobject]$result
}

function Test-AdbArgListPreservesLeadingDashes {
    $mkdir = New-AdbArgList -Items @('-s', 'X', 'shell', 'mkdir', '-p', '/data/x')
    if ($mkdir[4] -ne '-p') { throw "Sanity: -p not at index 4: $($mkdir -join ',')" }
    $start = New-AdbArgList -Items @(
        '-s', 'X', 'shell', 'am', 'start', '-W', '-n', 'a/b', '--activity-clear-task'
    )
    if ($start -notcontains '-W' -or $start -notcontains '-n' -or $start -notcontains '--activity-clear-task') {
        throw 'Sanity: am start flags lost'
    }
    $instr = New-AdbArgList -Items @(
        '-s', 'X', 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'C',
        '-e', 'smoke_run_id', 'r1', 'pkg/runner'
    )
    if ($instr -notcontains '-w' -or $instr -notcontains '-r' -or $instr -notcontains '-e') {
        throw 'Sanity: instrument flags lost'
    }
    if (($instr | Where-Object { $_ -eq '-e' }).Count -lt 2) {
        throw 'Sanity: expected multiple -e instrumentation flags'
    }
    Write-Host 'PS1_SANITY: leading-dash AdbArgs construction OK'
    return $true
}

function Test-InstrumentVerdictParserSanity {
    $passSample = @"
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS_CODE: 0
OK (1 test)
INSTRUMENTATION_CODE: -1
"@
    $pass = Get-ParsedInstrumentVerdict -RawOutput $passSample -HostExit 1
    if ($pass.PARSED_VERDICT -ne 'PASS') { throw "Sanity: timeout-like sample must PASS, got $($pass.PARSED_VERDICT)" }
    if (-not $pass.HOST_EXIT_DISAGREES) { throw 'Sanity: expected host exit disagreement on PASS+exit1' }

    $failSample = @"
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS_CODE: -2
FAILURES!!!
Tests run: 1,  Failures: 1
INSTRUMENTATION_CODE: -1
"@
    $fail = Get-ParsedInstrumentVerdict -RawOutput $failSample -HostExit 1
    if ($fail.PARSED_VERDICT -ne 'FAIL') { throw "Sanity: failure sample must FAIL, got $($fail.PARSED_VERDICT)" }
    Write-Host 'PS1_SANITY: instrument verdict parser OK'
    return $true
}

if ($SanityOnly) {
    Test-AdbArgListPreservesLeadingDashes | Out-Null
    Test-InstrumentVerdictParserSanity | Out-Null
    Write-Host 'ACTION_TOUR_PS1_SANITY = PASS'
    exit 0
}

function ConvertFrom-JavaPropertiesValue {
    param([string]$Value)
    if ([string]::IsNullOrEmpty($Value)) { return $Value }
    # Java .properties: \X → X  (\\ → \, \: → :, \= → =, …)
    return [regex]::Replace($Value, '\\(.)', { param($m) $m.Groups[1].Value })
}

function Get-AdbPath {
    $candidates = New-Object System.Collections.Generic.List[string]

    foreach ($envName in @('ANDROID_SDK_ROOT', 'ANDROID_HOME')) {
        $root = [Environment]::GetEnvironmentVariable($envName)
        if (-not [string]::IsNullOrWhiteSpace($root)) {
            $candidates.Add((Join-Path $root.Trim().Trim('"') 'platform-tools\adb.exe'))
        }
    }

    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) {
        $candidates.Add($cmd.Source)
    }

    $localProps = Join-Path $AndroidRoot 'local.properties'
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^\s*sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $raw = ($line -replace '^\s*sdk\.dir=', '').Trim().Trim("'").Trim('"')
            $sdk = ConvertFrom-JavaPropertiesValue -Value $raw
            if (-not [string]::IsNullOrWhiteSpace($sdk)) {
                $candidates.Add((Join-Path $sdk 'platform-tools\adb.exe'))
            }
        }
    }

    foreach ($path in $candidates) {
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        $full = $path
        try {
            if (-not [System.IO.Path]::IsPathRooted($full)) { continue }
        } catch {
            continue
        }
        if (Test-Path -LiteralPath $full) {
            return (Resolve-Path -LiteralPath $full).Path
        }
    }

    throw @"
STOP: adb.exe not found.
Tried: ANDROID_SDK_ROOT, ANDROID_HOME, Get-Command adb, android/local.properties (Java-unescaped sdk.dir).
"@
}

$Adb = Get-AdbPath
Write-Host "ADB=$Adb"

# Generic adb invocation: pass EVERY token via -AdbArgs @(...) so leading dashes
# (-s -p -W -n -e -r --activity-clear-task …) reach adb.exe literally.
function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AdbArgs
    )
    & $Adb @AdbArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed ($LASTEXITCODE): adb $($AdbArgs -join ' ')"
    }
}

function Invoke-AdbAllowFail {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AdbArgs
    )
    & $Adb @AdbArgs
    return $LASTEXITCODE
}

function Get-OnlineDevices {
    $lines = & $Adb devices -l
    $devices = @()
    foreach ($line in $lines) {
        if ($line -match '^(\S+)\s+device(\s|$)') {
            $devices += $Matches[1]
        }
    }
    return $devices
}

function Resolve-DeviceSerial {
    param([string]$Requested)
    $online = @(Get-OnlineDevices)
    if ($online.Count -eq 0) {
        Write-Host 'No online devices; attempting one-shot adb restart...'
        & $Adb kill-server | Out-Null
        Start-Sleep -Seconds 1
        & $Adb start-server | Out-Null
        Start-Sleep -Seconds 2
        $online = @(Get-OnlineDevices)
    }
    if ($online.Count -eq 0) {
        throw 'STOP: no online adb device (state=device) after one recovery attempt'
    }
    if ([string]::IsNullOrWhiteSpace($Requested)) {
        if ($online.Count -ne 1) {
            throw "STOP: expected exactly 1 online device, found $($online.Count): $($online -join ', ')"
        }
        return $online[0]
    }
    if ($online -notcontains $Requested) {
        throw "STOP: serial '$Requested' not online. Online: $($online -join ', ')"
    }
    return $Requested
}

function Get-ProductionVersionCode {
    param([string]$DeviceSerial)
    $dump = & $Adb -s $DeviceSerial shell dumpsys package $ProductionPackage
    $vc = ($dump | Select-String -Pattern 'versionCode=(\d+)').Matches | Select-Object -First 1
    if (-not $vc) { return 'unknown' }
    return $vc.Groups[1].Value
}

function Get-DeviceModel {
    param([string]$DeviceSerial)
    $model = (& $Adb -s $DeviceSerial shell getprop ro.product.model).ToString().Trim()
    if ([string]::IsNullOrWhiteSpace($model)) { return 'unknown' }
    return $model
}

function Write-Report {
    param(
        [string]$Path,
        [string]$Device,
        [string]$SerialValue,
        [string]$VersionCode,
        [hashtable]$Stages,
        [string]$ProcessCrash,
        [string]$FailedStage = '',
        [string]$Expected = '',
        [string]$Actual = '',
        [string]$Selector = '',
        [string]$Screenshot = '',
        [string[]]$ExtraLines = @()
    )
    $overall = 'PASS'
    if ($ProcessCrash -eq 'YES') { $overall = 'FAIL' }
    foreach ($k in @('START_ENTRY','INTRO','TASK_1_ARCHIVE','TASK_2_SCHEDULE','TASK_3_WORDING','TASK_4_SOUND','TASK_5_DEFER','GATE')) {
        if ($Stages[$k] -eq 'FAIL') { $overall = 'FAIL' }
    }
    if ($Stages['TIMEOUT_REGRESSION'] -eq 'FAIL') { $overall = 'FAIL' }

    $lines = @(
        "DEVICE = $Device"
        "SERIAL = $SerialValue"
        "PACKAGE = $ProductionPackage"
        "VERSION_CODE = $VersionCode"
        ''
        "START_ENTRY = $($Stages['START_ENTRY'])"
        "INTRO = $($Stages['INTRO'])"
        "TASK_1_ARCHIVE = $($Stages['TASK_1_ARCHIVE'])"
        "TASK_2_SCHEDULE = $($Stages['TASK_2_SCHEDULE'])"
        "TASK_3_WORDING = $($Stages['TASK_3_WORDING'])"
        "TASK_4_SOUND = $($Stages['TASK_4_SOUND'])"
        "TASK_5_DEFER = $($Stages['TASK_5_DEFER'])"
        "GATE = $($Stages['GATE'])"
        "TIMEOUT_REGRESSION = $($Stages['TIMEOUT_REGRESSION'])"
        ''
        "PROCESS_CRASH = $ProcessCrash"
        ''
        "ACTION_TOUR_DEVICE_SMOKE = $overall"
    )
    if ($ExtraLines -and $ExtraLines.Count -gt 0) {
        $lines += ''
        $lines += $ExtraLines
    }
    if ($FailedStage) {
        $lines += ''
        $lines += "FAILED_STAGE = $FailedStage"
        if ($Expected) { $lines += "EXPECTED = $Expected" }
        if ($Actual) { $lines += "ACTUAL = $Actual" }
        if ($Selector) { $lines += "SELECTOR = $Selector" }
        if ($Screenshot) { $lines += "SCREENSHOT = $Screenshot" }
    }
    Set-Content -Path $Path -Value ($lines -join "`r`n") -Encoding UTF8
    return $overall
}

# --- Preflight ---
Write-Host '=== ActionTour device smoke preflight ==='
$DeviceSerial = Resolve-DeviceSerial -Requested $Serial
Write-Host "DEVICE_SERIAL=$DeviceSerial"

$pathOut = & $Adb -s $DeviceSerial shell pm path $ProductionPackage 2>&1
if ($LASTEXITCODE -ne 0 -or ($pathOut -join '') -notmatch 'package:') {
    throw "STOP: production package not installed: $ProductionPackage"
}
$VersionCode = Get-ProductionVersionCode -DeviceSerial $DeviceSerial
$DeviceModel = Get-DeviceModel -DeviceSerial $DeviceSerial
Write-Host "PRODUCTION_VERSION_CODE=$VersionCode MODEL=$DeviceModel"

$Timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunId = $Timestamp
$OutDir = Join-Path $AndroidRoot "app\build\outputs\action-tour-device-smoke\$Timestamp"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
# Smoke app writes to its own external Documents dir (never /data/local/tmp).
$DeviceArtifactPath = "/sdcard/Android/data/$SmokeAppId/files/Documents/action-tour-device-smoke/$RunId"
Set-Content -Path (Join-Path $OutDir 'run_id.txt') -Value $RunId -Encoding UTF8
Set-Content -Path (Join-Path $OutDir 'device_artifact_path.txt') -Value $DeviceArtifactPath -Encoding UTF8

# Baseline logcat marker (scoped clear of buffer only — not production data)
Invoke-AdbAllowFail -AdbArgs @('-s', $DeviceSerial, 'logcat', '-c') | Out-Null
$LogcatPid = $null
$LogcatFile = Join-Path $OutDir 'logcat.txt'
$logcatProc = Start-Process -FilePath $Adb -ArgumentList @(
    '-s', $DeviceSerial, 'logcat', '-v', 'threadtime',
    '*:S', 'AndroidRuntime:E', 'ActivityManager:I', 'System.err:W'
) -NoNewWindow -PassThru -RedirectStandardOutput $LogcatFile
$LogcatPid = $logcatProc.Id

$PidBefore = (& $Adb @('-s', $DeviceSerial, 'shell', 'pidof', $ProductionPackage) 2>$null | Out-String).Trim()
# PidBefore is observational only — empty is valid cold-start, never crash evidence.

try {
    Write-Host '=== Build smoke APK + androidTest ==='
    Push-Location $AndroidRoot
    $env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'F:\Android\Android Studio\jbr' }
    & .\gradlew.bat :actiontour-smoke:assembleDebug :actiontour-smoke:assembleDebugAndroidTest --quiet
    if ($LASTEXITCODE -ne 0) { throw "Gradle assemble smoke failed: $LASTEXITCODE" }
    Pop-Location

    $AppApk = Join-Path $AndroidRoot 'actiontour-smoke\build\outputs\apk\debug\actiontour-smoke-debug.apk'
    $TestApk = Join-Path $AndroidRoot 'actiontour-smoke\build\outputs\apk\androidTest\debug\actiontour-smoke-debug-androidTest.apk'
    if (-not (Test-Path $AppApk)) { throw "Missing smoke app APK: $AppApk" }
    if (-not (Test-Path $TestApk)) { throw "Missing smoke androidTest APK: $TestApk" }

    Write-Host '=== Install smoke APKs only (production untouched) ==='
    Invoke-Adb -AdbArgs @('-s', $DeviceSerial, 'install', '-r', $AppApk)
    Invoke-Adb -AdbArgs @('-s', $DeviceSerial, 'install', '-r', $TestApk)

    function Invoke-Instrument([string]$ClassName, [string]$OutName) {
        $raw = Join-Path $OutDir $OutName
        Write-Host "INSTRUMENT $ClassName runId=$RunId"
        $instrArgs = New-AdbArgList -Items @(
            '-s', $DeviceSerial,
            'shell', 'am', 'instrument', '-w', '-r',
            '-e', 'class', $ClassName,
            '-e', 'smoke_run_id', $RunId,
            "$SmokeTestId/$Runner"
        )
        & $Adb @instrArgs | Tee-Object -FilePath $raw
        $hostExit = $LASTEXITCODE
        $text = if (Test-Path $raw) { Get-Content -Path $raw -Raw -ErrorAction SilentlyContinue } else { '' }
        $parsed = Get-ParsedInstrumentVerdict -RawOutput $text -HostExit $hostExit
        return [pscustomobject]@{
            HostExit = $hostExit
            OutPath = $raw
            Parsed = $parsed
        }
    }

    $mainRun = Invoke-Instrument -ClassName $MainClass -OutName 'instrument_main.txt'
    $timeoutRun = $null
    if ($SkipTimeoutRegression) {
        Write-Host 'TIMEOUT_REGRESSION skipped by flag'
    } else {
        $timeoutRun = Invoke-Instrument -ClassName $TimeoutClass -OutName 'instrument_timeout.txt'
    }

    # Pull smoke-owned external artifacts for this runId only
    Write-Host "PULL $DeviceArtifactPath"
    Invoke-AdbAllowFail -AdbArgs @('-s', $DeviceSerial, 'pull', $DeviceArtifactPath, "$OutDir\device") | Out-Null

    $PidAfter = (& $Adb @('-s', $DeviceSerial, 'shell', 'pidof', $ProductionPackage) 2>$null | Out-String).Trim()
    $ExitInfoFile = Join-Path $OutDir 'exit-info.txt'
    & $Adb @('-s', $DeviceSerial, 'shell', 'dumpsys', 'activity', 'exit-info', $ProductionPackage) |
        Out-File -FilePath $ExitInfoFile -Encoding utf8

    $processCrash = 'NO'
    $logText = ''
    if (Test-Path $LogcatFile) {
        $logText = Get-Content $LogcatFile -Raw -ErrorAction SilentlyContinue
    }
    if ($logText -match 'FATAL EXCEPTION' -or $logText -match 'No destination with route settings') {
        if ($logText -match $ProductionPackage -or $logText -match 'No destination with route settings') {
            $processCrash = 'YES'
        }
    }
    $mainParsedFail = ($mainRun.Parsed.PARSED_VERDICT -eq 'FAIL')
    if ($PidBefore -and -not $PidAfter -and $mainParsedFail) {
        $exitInfo = Get-Content $ExitInfoFile -Raw -ErrorAction SilentlyContinue
        if ($exitInfo -match 'status=CRASH|reason=CRASH|description=crash') {
            $processCrash = 'YES'
        }
    }

    # Merge stages from device pull / instrument output
    $stages = @{
        START_ENTRY = 'PENDING'
        INTRO = 'PENDING'
        TASK_1_ARCHIVE = 'PENDING'
        TASK_2_SCHEDULE = 'PENDING'
        TASK_3_WORDING = 'PENDING'
        TASK_4_SOUND = 'PENDING'
        TASK_5_DEFER = 'PENDING'
        GATE = 'PENDING'
        TIMEOUT_REGRESSION = $(if ($SkipTimeoutRegression) { 'SKIPPED' } else { 'PENDING' })
    }

    $stageProps = Get-ChildItem -Path $OutDir -Recurse -Filter 'stages.properties' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($stageProps) {
        Get-Content $stageProps.FullName | ForEach-Object {
            if ($_ -match '^([A-Z0-9_]+)=(PASS|FAIL|SKIPPED|PENDING)$') {
                $stages[$Matches[1]] = $Matches[2]
            }
        }
    }

    $failedMeta = Get-ChildItem -Path $OutDir -Recurse -Filter 'failure_meta.txt' -ErrorAction SilentlyContinue | Select-Object -First 1
    $failedStage = ''; $expected = ''; $actual = ''; $selector = ''; $screenshot = ''
    if ($failedMeta) {
        Get-Content $failedMeta.FullName | ForEach-Object {
            if ($_ -match '^FAILED_STAGE=(.+)$') { $failedStage = $Matches[1] }
            if ($_ -match '^EXPECTED=(.+)$') { $expected = $Matches[1] }
            if ($_ -match '^ACTUAL=(.+)$') { $actual = $Matches[1] }
            if ($_ -match '^SELECTOR=(.+)$') { $selector = $Matches[1] }
            if ($_ -match '^SCREENSHOT=(.+)$') { $screenshot = $Matches[1] }
        }
    }

    # Main: on PASS fill remaining PENDING→PASS; on FAIL leave PENDING (not executed) — do not cascade FAIL.
    if ($mainRun.Parsed.PARSED_VERDICT -eq 'PASS') {
        foreach ($k in @('START_ENTRY','INTRO','TASK_1_ARCHIVE','TASK_2_SCHEDULE','TASK_3_WORDING','TASK_4_SOUND','TASK_5_DEFER','GATE')) {
            if ($stages[$k] -eq 'PENDING') { $stages[$k] = 'PASS' }
        }
    } else {
        if (-not $failedStage) {
            foreach ($k in @('START_ENTRY','INTRO','TASK_1_ARCHIVE','TASK_2_SCHEDULE','TASK_3_WORDING','TASK_4_SOUND','TASK_5_DEFER','GATE')) {
                if ($stages[$k] -eq 'FAIL') { $failedStage = $k; break }
            }
            if (-not $failedStage) { $failedStage = 'MAIN_SMOKE' }
        }
    }

    if (-not $SkipTimeoutRegression -and $null -ne $timeoutRun) {
        if ($timeoutRun.Parsed.PARSED_VERDICT -eq 'PASS') {
            if ($stages['TIMEOUT_REGRESSION'] -eq 'PENDING') {
                $stages['TIMEOUT_REGRESSION'] = 'PASS'
            }
            # Do not overwrite an explicit stage FAIL from properties with host-exit noise.
        } else {
            $stages['TIMEOUT_REGRESSION'] = 'FAIL'
            if (-not $failedStage) { $failedStage = 'TIMEOUT_REGRESSION' }
        }
        # Stage FAIL in properties wins even if stream looks OK
        if ($stages['TIMEOUT_REGRESSION'] -eq 'FAIL' -and $timeoutRun.Parsed.PARSED_VERDICT -eq 'PASS') {
            if (-not $failedStage) { $failedStage = 'TIMEOUT_REGRESSION' }
        }
    }

    # Prefer pulled failure.png
    $pulledFail = Get-ChildItem -Path $OutDir -Recurse -Filter 'failure_*.png' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pulledFail) {
        Copy-Item $pulledFail.FullName (Join-Path $OutDir 'failure.png') -Force
        if (-not $screenshot) { $screenshot = $pulledFail.FullName }
    }

    $extra = @(
        "MAIN_INSTRUMENT_HOST_EXIT = $($mainRun.HostExit)"
        "MAIN_PARSED_INSTRUMENT_VERDICT = $($mainRun.Parsed.PARSED_VERDICT)"
        "MAIN_HOST_EXIT_DISAGREES = $($mainRun.Parsed.HOST_EXIT_DISAGREES)"
        "MAIN_PARSE_REASON = $($mainRun.Parsed.REASON)"
    )
    if ($null -ne $timeoutRun) {
        $extra += "TIMEOUT_INSTRUMENT_HOST_EXIT = $($timeoutRun.HostExit)"
        $extra += "TIMEOUT_PARSED_INSTRUMENT_VERDICT = $($timeoutRun.Parsed.PARSED_VERDICT)"
        $extra += "TIMEOUT_HOST_EXIT_DISAGREES = $($timeoutRun.Parsed.HOST_EXIT_DISAGREES)"
        $extra += "TIMEOUT_PARSE_REASON = $($timeoutRun.Parsed.REASON)"
    }

    $reportPath = Join-Path $OutDir 'report.txt'
    $overall = Write-Report -Path $reportPath -Device $DeviceModel -SerialValue $DeviceSerial `
        -VersionCode $VersionCode -Stages $stages -ProcessCrash $processCrash `
        -FailedStage $failedStage -Expected $expected -Actual $actual -Selector $selector -Screenshot $screenshot `
        -ExtraLines $extra

    Write-Host "=== REPORT: $reportPath ==="
    Get-Content $reportPath | Write-Host
    Write-Host "ARTIFACTS=$OutDir"

    if ($overall -ne 'PASS') {
        exit 1
    }
    exit 0
}
finally {
    if ($LogcatPid) {
        Stop-Process -Id $LogcatPid -Force -ErrorAction SilentlyContinue
    }
    Pop-Location -ErrorAction SilentlyContinue
}
