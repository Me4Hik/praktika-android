# 13.08.2026 Pre-play tester release cursor by Me4Hik START
# Creates a release-signed production APK package for manual Telegram
# distribution to preliminary testers (NOT a Google Play release).
#
# Does NOT bump versionCode/versionName, does NOT edit source,
# does NOT send Telegram, does NOT write DISTRIBUTION_LOG.
<#
.SYNOPSIS
    Package a pre-play preliminary tester APK into release\testers\.

.PARAMETER Notes
    Optional one-line note stored in BUILD_INFO.txt.

.PARAMETER PreflightOnly
    Resolve tools/paths, inspect archive history, print next sequence
    and minimum versionCode. No build, no archive/CURRENT mutation.
#>
[CmdletBinding()]
param(
    [string]$Notes = '',
    [switch]$PreflightOnly
)

$ErrorActionPreference = 'Stop'

# --- Constants ---------------------------------------------------------------
$ExpectedPackage = 'com.me4hik.praktika'
$ExpectedCertSha256 = 'd35140e9c25902b0b865600bc594dee6debb3e3e8270744d69edc5e867a9ad5d'
$ExpectedVersionName = '1.0'
$BaselineVersionCode = 7
$ArchiveNameRegex = '^t(?<seq>\d{3})-vc(?<vc>\d+)-(?<date>\d{4}-\d{2}-\d{2})$'
$DefaultJbr = '<JAVA_HOME>'
$DefaultSdkFallback = '<ANDROID_SDK>'

# --- Path resolution (independent of cwd) ------------------------------------
$ScriptDir = $PSScriptRoot
$AndroidRoot = Split-Path -Parent $ScriptDir
$RepoRoot = Split-Path -Parent $AndroidRoot
$TesterRoot = Join-Path $RepoRoot 'release\testers'
$CurrentDir = Join-Path $TesterRoot 'CURRENT'
$ArchiveRoot = Join-Path $TesterRoot 'archive'
$StagingRoot = Join-Path $TesterRoot '.staging'
$BuiltApkPath = Join-Path $AndroidRoot 'app\build\outputs\apk\production\release\app-production-release.apk'
$Gradlew = Join-Path $AndroidRoot 'gradlew.bat'

function Write-Fail([string]$Code, [string]$Message) {
    Write-Host ''
    Write-Host "TESTER_RELEASE_NOT_CREATED = $Code"
    Write-Host $Message
    Write-Host ''
    throw "TESTER_RELEASE_NOT_CREATED: $Code"
}

function Normalize-Sha256([string]$Raw) {
    if ([string]::IsNullOrWhiteSpace($Raw)) { return '' }
    return ($Raw -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
}

function Get-SdkDir {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT) -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        return $env:ANDROID_SDK_ROOT
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME) -and (Test-Path $env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }
    $localProps = Join-Path $AndroidRoot 'local.properties'
    if (Test-Path $localProps) {
        foreach ($line in Get-Content -Path $localProps -Encoding UTF8) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)\s*$') {
                $raw = $Matches[1].Trim()
                # local.properties uses escaped Windows paths: C\:\\Users\\...
                $decoded = $raw -replace '\\\\', '\' -replace '\\:', ':'
                if (Test-Path $decoded) { return $decoded }
            }
        }
    }
    if (Test-Path $DefaultSdkFallback) { return $DefaultSdkFallback }
    return $null
}

function Get-BuildToolsBin {
    param([string]$SdkDir)
    $btRoot = Join-Path $SdkDir 'build-tools'
    if (-not (Test-Path $btRoot)) {
        Write-Fail 'BUILD_TOOLS_MISSING' "Android build-tools not found under: $btRoot"
    }
    $versions = Get-ChildItem -Path $btRoot -Directory | Sort-Object {
        # Prefer numeric version sort; fall back to name
        $parts = $_.Name -split '[^0-9]+' | Where-Object { $_ -ne '' } | ForEach-Object { [int]$_ }
        [version](($parts + @(0, 0, 0))[0..2] -join '.')
    }
    if (-not $versions -or $versions.Count -eq 0) {
        Write-Fail 'BUILD_TOOLS_MISSING' "No build-tools versions under: $btRoot"
    }
    $latest = $versions[-1].FullName
    $aapt = Join-Path $latest 'aapt.exe'
    $apksigner = Join-Path $latest 'apksigner.bat'
    if (-not (Test-Path $apksigner)) {
        Write-Fail 'APKSIGNER_MISSING' "apksigner.bat not found in: $latest"
    }
    # Prefer aapt.exe (same as existing production deploy scripts).
    if (-not (Test-Path $aapt)) {
        Write-Fail 'AAPT_MISSING' "aapt.exe not found in: $latest"
    }
    return [pscustomobject]@{
        Dir       = $latest
        Aapt      = $aapt
        Apksigner = $apksigner
    }
}

function Ensure-JavaHome {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME) -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return
    }
    if (Test-Path (Join-Path $DefaultJbr 'bin\java.exe')) {
        $env:JAVA_HOME = $DefaultJbr
        $env:PATH = "$DefaultJbr\bin;$env:PATH"
        return
    }
    Write-Fail 'JAVA_HOME_MISSING' "JAVA_HOME not set and default JBR missing: $DefaultJbr"
}

function Get-GitMeta {
    $commit = 'unavailable'
    $branch = 'unavailable'
    $dirty = 'UNKNOWN'
    Push-Location $RepoRoot
    try {
        $c = & git rev-parse HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $c) { $commit = $c.Trim() }
        $b = & git rev-parse --abbrev-ref HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $b) { $branch = $b.Trim() }
        $status = & git status --porcelain 2>$null
        if ($LASTEXITCODE -eq 0) {
            $dirty = if ([string]::IsNullOrWhiteSpace(($status | Out-String).Trim())) { 'NO' } else { 'YES' }
        }
    } catch {
        # leave unavailable
    } finally {
        Pop-Location
    }
    return [pscustomobject]@{ Commit = $commit; Branch = $branch; Dirty = $dirty }
}

function Get-ArchiveHistory {
    if (-not (Test-Path $ArchiveRoot)) {
        New-Item -ItemType Directory -Force -Path $ArchiveRoot | Out-Null
    }
    $dirs = @(Get-ChildItem -Path $ArchiveRoot -Directory -ErrorAction SilentlyContinue)
    $entries = @()
    $nonCanonical = @()
    foreach ($d in $dirs) {
        if ($d.Name -match $ArchiveNameRegex) {
            $entries += [pscustomobject]@{
                Name = $d.Name
                Seq  = [int]$Matches['seq']
                Vc   = [int]$Matches['vc']
                Date = $Matches['date']
                Path = $d.FullName
            }
        } else {
            $nonCanonical += $d.Name
        }
    }
    if ($nonCanonical.Count -gt 0) {
        Write-Fail 'ARCHIVE_AMBIGUOUS' (
            "Non-canonical archive folder name(s) found. STOP without guessing.`n" +
            ($nonCanonical -join "`n")
        )
    }
    $seqGroups = $entries | Group-Object Seq | Where-Object { $_.Count -gt 1 }
    if ($seqGroups) {
        Write-Fail 'ARCHIVE_AMBIGUOUS' (
            "Duplicate tester sequence(s) in archive: " +
            (($seqGroups | ForEach-Object { 't{0:D3}' -f $_.Name }) -join ', ')
        )
    }
    $vcGroups = $entries | Group-Object Vc | Where-Object { $_.Count -gt 1 }
    if ($vcGroups) {
        Write-Fail 'ARCHIVE_AMBIGUOUS' (
            "Duplicate versionCode(s) in archive: " +
            (($vcGroups | ForEach-Object { "vc$($_.Name)" }) -join ', ')
        )
    }
    $maxSeq = 0
    $maxVc = $BaselineVersionCode
    if ($entries.Count -gt 0) {
        $maxSeq = ($entries | Measure-Object -Property Seq -Maximum).Maximum
        $archivedMaxVc = ($entries | Measure-Object -Property Vc -Maximum).Maximum
        if ($archivedMaxVc -gt $maxVc) { $maxVc = $archivedMaxVc }
    }
    return [pscustomobject]@{
        Entries       = $entries
        MaxSeq        = $maxSeq
        HighestVcGate = $maxVc
        NextSeq       = $maxSeq + 1
        MinNextVc     = $maxVc + 1
    }
}

function Format-TesterSeq([int]$Seq) {
    if ($Seq -lt 1 -or $Seq -gt 999) {
        Write-Fail 'SEQUENCE_OUT_OF_RANGE' "Tester sequence out of range (1..999): $Seq"
    }
    return ('t{0:D3}' -f $Seq)
}

function Get-ApkBadging {
    param(
        [string]$ApkPath,
        $Tools
    )
    $out = & $Tools.Aapt dump badging $ApkPath 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        Write-Fail 'AAPT_FAILED' "aapt dump badging failed:`n$out"
    }
    $pkg = $null
    $vc = $null
    $vn = $null
    if ($out -match "package: name='([^']+)'") { $pkg = $Matches[1] }
    if ($out -match "versionCode='(\d+)'") { $vc = [int]$Matches[1] }
    if ($out -match "versionName='([^']*)'") { $vn = $Matches[1] }
    if (-not $pkg -or $null -eq $vc -or $null -eq $vn) {
        Write-Fail 'APK_METADATA_PARSE' "Could not parse package/versionCode/versionName from badging.`n$out"
    }
    return [pscustomobject]@{ Package = $pkg; VersionCode = $vc; VersionName = $vn; Raw = $out }
}

function Get-ApkCertSha256 {
    param(
        [string]$ApkPath,
        $Tools
    )
    $out = & $Tools.Apksigner verify --print-certs $ApkPath 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        Write-Fail 'APKSIGNER_FAILED' "apksigner verify --print-certs failed:`n$out"
    }
    $cert = $null
    # build-tools may print either "Signer #1 certificate SHA-256 digest:"
    # or "V2 Signer: certificate SHA-256 digest:" (observed on 37.0.0).
    if ($out -match '(?:Signer\s+#1|V\d+\s+Signer:) certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)') {
        $cert = Normalize-Sha256 $Matches[1]
    }
    if ([string]::IsNullOrWhiteSpace($cert)) {
        Write-Fail 'CERT_PARSE' "Could not parse certificate SHA-256 from apksigner output.`n$out"
    }
    return $cert
}

function Get-FileSha256Hex([string]$Path) {
    $hash = Get-FileHash -Path $Path -Algorithm SHA256
    return $hash.Hash.ToLowerInvariant()
}

function New-BuildInfoText {
    param(
        [string]$TesterBuild,
        [int]$VersionCode,
        [string]$VersionName,
        [string]$BuiltAt,
        [string]$GitCommit,
        [string]$SourceBranch,
        [string]$ApkSha256,
        [string]$CertSha256,
        [string]$Dirty,
        [string]$NotesText
    )
    $notesLine = if ([string]::IsNullOrWhiteSpace($NotesText)) { '' } else { $NotesText.Trim() }
    return @"
Tester build: $TesterBuild
Version code: $VersionCode
Version name: $VersionName
Package: $ExpectedPackage
Built at: $BuiltAt
Git commit: $GitCommit
Source branch: $SourceBranch
Working tree dirty: $Dirty
APK SHA-256: $ApkSha256
Signing cert SHA-256: $CertSha256
Notes: $notesLine
"@
}

function Assert-DirectoryLayout {
    foreach ($p in @($TesterRoot, $CurrentDir, $ArchiveRoot)) {
        if (-not (Test-Path $p)) {
            New-Item -ItemType Directory -Force -Path $p | Out-Null
        }
    }
}

function Invoke-ProductionReleaseBuild {
    if (-not (Test-Path $Gradlew)) {
        Write-Fail 'GRADLEW_MISSING' "gradlew.bat not found: $Gradlew"
    }
    Ensure-JavaHome
    Push-Location $AndroidRoot
    try {
        Write-Host "Building :app:assembleProductionRelease ..."
        & .\gradlew.bat :app:assembleProductionRelease --no-daemon
        if ($LASTEXITCODE -ne 0) {
            Write-Fail 'GRADLE_BUILD_FAILED' "assembleProductionRelease failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path $BuiltApkPath)) {
        Write-Fail 'APK_MISSING' "Expected APK not found after build: $BuiltApkPath"
    }
}

function Replace-CurrentAtomically {
    param(
        [string]$SourceStagingCurrent,
        [string]$ExpectedApkName
    )
    $nextDir = Join-Path $TesterRoot 'CURRENT.next'
    $bakDir = Join-Path $TesterRoot ("CURRENT.bak-" + [guid]::NewGuid().ToString('N').Substring(0, 8))

    if (Test-Path $nextDir) { Remove-Item -Recurse -Force $nextDir }
    Copy-Item -Path $SourceStagingCurrent -Destination $nextDir -Recurse -Force

    $nextFiles = @(Get-ChildItem -Path $nextDir -File)
    $apkFiles = @($nextFiles | Where-Object { $_.Name -like '*.apk' })
    $infoFiles = @($nextFiles | Where-Object { $_.Name -eq 'BUILD_INFO.txt' })
    if ($apkFiles.Count -ne 1 -or $apkFiles[0].Name -ne $ExpectedApkName) {
        Remove-Item -Recurse -Force $nextDir -ErrorAction SilentlyContinue
        Write-Fail 'CURRENT_NEXT_INVALID' "CURRENT.next must contain exactly one APK named $ExpectedApkName"
    }
    if ($infoFiles.Count -ne 1 -or $nextFiles.Count -ne 2) {
        Remove-Item -Recurse -Force $nextDir -ErrorAction SilentlyContinue
        Write-Fail 'CURRENT_NEXT_INVALID' 'CURRENT.next must contain exactly APK + BUILD_INFO.txt'
    }

    $hadCurrent = Test-Path $CurrentDir
    try {
        if ($hadCurrent) {
            Move-Item -Path $CurrentDir -Destination $bakDir
        }
        Move-Item -Path $nextDir -Destination $CurrentDir
    } catch {
        # Attempt restore
        if (-not (Test-Path $CurrentDir) -and (Test-Path $bakDir)) {
            Move-Item -Path $bakDir -Destination $CurrentDir -ErrorAction SilentlyContinue
        }
        if (Test-Path $nextDir) {
            Remove-Item -Recurse -Force $nextDir -ErrorAction SilentlyContinue
        }
        Write-Fail 'CURRENT_REPLACE_FAILED' "Failed to replace CURRENT; previous CURRENT restore attempted. $_"
    }

    if (Test-Path $bakDir) {
        Remove-Item -Recurse -Force $bakDir
    }
}

# --- Main --------------------------------------------------------------------
Assert-DirectoryLayout

$sdkDir = Get-SdkDir
if (-not $sdkDir) {
    Write-Fail 'SDK_MISSING' 'Android SDK not found (ANDROID_SDK_ROOT / ANDROID_HOME / local.properties / fallback).'
}
$tools = Get-BuildToolsBin -SdkDir $sdkDir
$history = Get-ArchiveHistory
$nextSeqLabel = Format-TesterSeq $history.NextSeq

Write-Host "TESTER_RELEASE_ROOT = $TesterRoot"
Write-Host "NEXT_TESTER_SEQUENCE = $nextSeqLabel"
Write-Host "HIGHEST_ARCHIVED_OR_BASELINE_VC = $($history.HighestVcGate)"
Write-Host "NEXT_REQUIRED_VERSION_CODE = $($history.MinNextVc)"
Write-Host "EXPECTED_VERSION_NAME = $ExpectedVersionName"
Write-Host "EXPECTED_PACKAGE = $ExpectedPackage"
Write-Host "BUILD_TOOLS = $($tools.Dir)"

if ($PreflightOnly) {
    Write-Host ''
    Write-Host 'PREFLIGHT_ONLY = YES'
    Write-Host 'NO_BUILD = YES'
    Write-Host 'NO_ARCHIVE_MUTATION = YES'
    Write-Host 'NO_CURRENT_MUTATION = YES'
    Write-Host "To create a release: intentionally bump versionCode to at least $($history.MinNextVc), then re-run without -PreflightOnly."
    exit 0
}

# Build
Invoke-ProductionReleaseBuild

# Verify built APK identity
$meta = Get-ApkBadging -ApkPath $BuiltApkPath -Tools $tools
$cert = Get-ApkCertSha256 -ApkPath $BuiltApkPath -Tools $tools

if ($meta.Package -ne $ExpectedPackage) {
    Write-Fail 'PACKAGE_MISMATCH' "Built APK package='$($meta.Package)' expected='$ExpectedPackage'"
}
if ($meta.VersionName -ne $ExpectedVersionName) {
    Write-Fail 'VERSION_NAME_POLICY' (
        "Built APK versionName='$($meta.VersionName)' does not match pipeline policy '$ExpectedVersionName'. " +
        'Do not silently normalize; fix source versionName or update the agreed policy.'
    )
}
if ($cert -ne $ExpectedCertSha256) {
    Write-Fail 'CERT_MISMATCH' "Built APK signing cert SHA-256='$cert' expected='$ExpectedCertSha256'"
}

# Re-read archive after build (still no mutation yet) and enforce monotonicity
$history = Get-ArchiveHistory
$nextSeqLabel = Format-TesterSeq $history.NextSeq
$minVc = $history.MinNextVc
$vc = $meta.VersionCode

if ($vc -le $history.HighestVcGate) {
    Write-Fail 'VERSION_CODE_NOT_MONOTONIC' (
        "Tester release not created. Current versionCode=$vc is not greater than highest archived/baseline tester versionCode=$($history.HighestVcGate). " +
        "Intentionally bump versionCode before creating another tester release.`n" +
        "NEXT_REQUIRED_VERSION_CODE = $minVc"
    )
}

# Prepare names
$today = Get-Date -Format 'yyyy-MM-dd'
$apkFileName = "Praktika-preplay-$nextSeqLabel-vc$vc.apk"
$archiveFolderName = "$nextSeqLabel-vc$vc-$today"
$archiveDir = Join-Path $ArchiveRoot $archiveFolderName

if (Test-Path $archiveDir) {
    Write-Fail 'ARCHIVE_EXISTS' "Archive directory already exists (immutable; will not overwrite): $archiveDir"
}

$apkSha = Get-FileSha256Hex $BuiltApkPath
$git = Get-GitMeta
$builtAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')
$buildInfo = New-BuildInfoText `
    -TesterBuild $nextSeqLabel `
    -VersionCode $vc `
    -VersionName $meta.VersionName `
    -BuiltAt $builtAt `
    -GitCommit $git.Commit `
    -SourceBranch $git.Branch `
    -ApkSha256 $apkSha `
    -CertSha256 $cert `
    -Dirty $git.Dirty `
    -NotesText $Notes

$sha256Txt = "$apkSha  $apkFileName`r`n"

# Staging: complete package before touching archive or CURRENT
if (Test-Path $StagingRoot) { Remove-Item -Recurse -Force $StagingRoot }
$stageArchive = Join-Path $StagingRoot 'archive-entry'
$stageCurrent = Join-Path $StagingRoot 'current-entry'
New-Item -ItemType Directory -Force -Path $stageArchive | Out-Null
New-Item -ItemType Directory -Force -Path $stageCurrent | Out-Null

Copy-Item -Path $BuiltApkPath -Destination (Join-Path $stageArchive $apkFileName)
Set-Content -Path (Join-Path $stageArchive 'BUILD_INFO.txt') -Value $buildInfo -Encoding UTF8
Set-Content -Path (Join-Path $stageArchive 'SHA256.txt') -Value $sha256Txt -Encoding UTF8 -NoNewline

Copy-Item -Path $BuiltApkPath -Destination (Join-Path $stageCurrent $apkFileName)
Set-Content -Path (Join-Path $stageCurrent 'BUILD_INFO.txt') -Value $buildInfo -Encoding UTF8

# Create immutable archive (Move so we never merge into existing)
try {
    Move-Item -Path $stageArchive -Destination $archiveDir
} catch {
    Write-Fail 'ARCHIVE_CREATE_FAILED' "Failed to create immutable archive at $archiveDir : $_"
}

if (-not (Test-Path (Join-Path $archiveDir $apkFileName)) -or
    -not (Test-Path (Join-Path $archiveDir 'BUILD_INFO.txt')) -or
    -not (Test-Path (Join-Path $archiveDir 'SHA256.txt'))) {
    Write-Fail 'ARCHIVE_INCOMPLETE' "Archive created but required files missing: $archiveDir"
}

# Replace CURRENT only after archive success
Replace-CurrentAtomically -SourceStagingCurrent $stageCurrent -ExpectedApkName $apkFileName

# Final CURRENT validation
$currentFiles = @(Get-ChildItem -Path $CurrentDir -File)
$currentApks = @($currentFiles | Where-Object { $_.Extension -eq '.apk' })
if ($currentApks.Count -ne 1 -or $currentApks[0].Name -ne $apkFileName) {
    Write-Fail 'CURRENT_INVALID' "CURRENT must contain exactly one APK named $apkFileName"
}
if (-not (Test-Path (Join-Path $CurrentDir 'BUILD_INFO.txt')) -or $currentFiles.Count -ne 2) {
    Write-Fail 'CURRENT_INVALID' 'CURRENT must contain exactly APK + BUILD_INFO.txt'
}

# Cleanup staging
if (Test-Path $StagingRoot) { Remove-Item -Recurse -Force $StagingRoot }

$apkToSend = Join-Path $CurrentDir $apkFileName
Write-Host ''
Write-Host '============================================================'
Write-Host "TESTER_RELEASE_CREATED = $nextSeqLabel"
Write-Host "VERSION_CODE = $vc"
Write-Host "VERSION_NAME = $($meta.VersionName)"
Write-Host "APK_TO_SEND ="
Write-Host $apkToSend
Write-Host "ARCHIVE ="
Write-Host "$archiveDir\"
Write-Host "SHA256 ="
Write-Host $apkSha
Write-Host "CURRENT_APK_COUNT = 1"
Write-Host '============================================================'
Write-Host 'Send ONLY the APK_TO_SEND path via Telegram after manual confirmation.'
Write-Host 'Then append a row to release\testers\DISTRIBUTION_LOG.txt (script does not).'
Write-Host ''
# 13.08.2026 Pre-play tester release cursor by Me4Hik END
