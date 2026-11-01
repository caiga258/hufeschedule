#Requires -Version 5.1
<#
.SYNOPSIS
    Cut a release: bump version numbers, archive CHANGELOG, commit and tag.

.DESCRIPTION
    Sources of truth (must stay in sync with the git tag):
      androidApp/build.gradle.kts : versionCode (auto +1) and versionName
      desktopApp/build.gradle.kts : appVersion
      CHANGELOG.md               : [Unreleased] -> [X.Y.Z] - yyyy-MM-dd

.PARAMETER Version
    Release version, e.g. 2.2.0 (no leading "v").

.PARAMETER DryRun
    Print what would change, write nothing, do not commit or tag.

.EXAMPLE
    pwsh scripts/release.ps1 -Version 2.2.0 -DryRun

.EXAMPLE
    pwsh scripts/release.ps1 -Version 2.2.0
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    Write-Host "ERROR: $Message" -ForegroundColor Red
    exit 1
}

$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Fail "Version must look like 2.2.0 (got '$Version')."
}

$androidRel = 'androidApp/build.gradle.kts'
$desktopRel = 'desktopApp/build.gradle.kts'
$changelogRel = 'CHANGELOG.md'

foreach ($rel in @($androidRel, $desktopRel, $changelogRel)) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $rel))) { Fail "Missing file: $rel" }
}

$dirty = git status --porcelain
if ($dirty) {
    Write-Host $dirty
    Fail 'Working tree is dirty. Commit or stash your changes before releasing.'
}

$tag = "v$Version"
if (git tag --list $tag) { Fail "Tag $tag already exists." }

$utf8 = New-Object System.Text.UTF8Encoding($false)

# --- Android: versionCode +1, versionName = <Version> -----------------------
$androidPath = Join-Path $root $androidRel
$androidText = [System.IO.File]::ReadAllText($androidPath)

if ($androidText -notmatch 'versionCode\s*=\s*(\d+)') {
    Fail "Cannot find 'versionCode = N' in $androidRel"
}
$oldCode = [int]$Matches[1]
$newCode = $oldCode + 1

if ($androidText -notmatch 'versionName\s*=\s*"([^"]+)"') {
    Fail "Cannot find 'versionName = ""x.y.z""' in $androidRel"
}
$oldName = $Matches[1]

$androidNew = [regex]::Replace($androidText, 'versionCode\s*=\s*\d+', "versionCode = $newCode")
$androidNew = [regex]::Replace($androidNew, 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`"")

# --- Desktop: appVersion ----------------------------------------------------
$desktopPath = Join-Path $root $desktopRel
$desktopText = [System.IO.File]::ReadAllText($desktopPath)

if ($desktopText -notmatch 'val\s+appVersion\s*=\s*"([^"]+)"') {
    Fail "Cannot find 'val appVersion = ""x.y.z""' in $desktopRel"
}
$oldDesktop = $Matches[1]
$desktopNew = [regex]::Replace($desktopText, 'val\s+appVersion\s*=\s*"[^"]+"', "val appVersion = `"$Version`"")

# --- CHANGELOG: close [Unreleased], open a new empty one --------------------
$changelogPath = Join-Path $root $changelogRel
$changelogText = [System.IO.File]::ReadAllText($changelogPath)

$marker = '## [Unreleased]'
$markerIndex = $changelogText.IndexOf($marker, [System.StringComparison]::Ordinal)
if ($markerIndex -lt 0) { Fail "CHANGELOG.md has no '$marker' section." }
if ($changelogText.Contains("## [$Version]")) { Fail "CHANGELOG.md already has a [$Version] section." }

$today = Get-Date -Format 'yyyy-MM-dd'
$nl = "`n"   # keep LF: the repo enforces eol=lf, Windows CRLF would break consistency
$block = @(
    '## [Unreleased]'
    ''
    "## [$Version] - $today"
) -join $nl

$changelogNew = $changelogText.Remove($markerIndex, $marker.Length).Insert($markerIndex, $block)

# --- Report / write ---------------------------------------------------------
Write-Host ''
Write-Host "Release $tag" -ForegroundColor Cyan
Write-Host ("  versionCode : {0} -> {1}" -f $oldCode, $newCode)
Write-Host ("  versionName : {0} -> {1}" -f $oldName, $Version)
Write-Host ("  appVersion  : {0} -> {1}" -f $oldDesktop, $Version)
Write-Host ("  CHANGELOG   : [Unreleased] -> [$Version] - $today")
Write-Host ''

if ($DryRun) {
    Write-Host 'Dry run: nothing was written.' -ForegroundColor Yellow
    exit 0
}

[System.IO.File]::WriteAllText($androidPath, $androidNew, $utf8)
[System.IO.File]::WriteAllText($desktopPath, $desktopNew, $utf8)
[System.IO.File]::WriteAllText($changelogPath, $changelogNew, $utf8)

git add -- $androidRel $desktopRel $changelogRel
if ($LASTEXITCODE -ne 0) { Fail 'git add failed.' }

git commit -m "chore(release): $tag"
if ($LASTEXITCODE -ne 0) { Fail 'git commit failed. Version files were edited; check `git status`.' }

git tag -a $tag -m "release $Version"
if ($LASTEXITCODE -ne 0) { Fail 'git tag failed; the release commit exists but is untagged.' }

Write-Host ''
Write-Host "Done: $tag" -ForegroundColor Green
Write-Host 'Next steps:'
Write-Host '  git push'
Write-Host "  git push origin $tag"
Write-Host '  ./gradlew :androidApp:assembleRelease'
