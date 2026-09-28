param(
    [switch]$InitializeBaseline,
    [string]$Request,
    [switch]$Install,
    [switch]$AllowCodeMods,
    [string]$ApiBaseUrl = 'https://signal-feed.zhx100560.workers.dev/'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$baseId = 'signalfeed-0.7.0'
$baseDir = Join-Path $project 'mods/baselines'
$baseZip = Join-Path $baseDir "$baseId.zip"
$baseInfo = Join-Path $baseDir "$baseId.json"
function Resolve-Child([string]$root, [string]$relative) {
    if ([string]::IsNullOrWhiteSpace($relative) -or $relative.Contains('\') -or $relative.Contains(':') -or $relative.StartsWith('/') -or $relative.Split('/') -contains '..') { throw "Invalid archive path: $relative" }
    $full = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (!$full.StartsWith([IO.Path]::GetFullPath($root).TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Path escaped workspace' }
    return $full
}
function Expand-Safe([string]$archivePath, [string]$destination) {
    [IO.Directory]::CreateDirectory($destination) | Out-Null
    $zip = [IO.Compression.ZipFile]::OpenRead($archivePath)
    try {
        $total = 0L
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        if ($zip.Entries.Count -gt 3000) { throw 'Too many files' }
        foreach ($entry in $zip.Entries) {
            $target = Resolve-Child $destination $entry.FullName
            if (!$seen.Add($target)) { throw 'Duplicate ZIP entry' }
            $total += $entry.Length
            if ($total -gt 128MB) { throw 'Archive exceeds 128 MB' }
            if ($entry.FullName.EndsWith('/')) { [IO.Directory]::CreateDirectory($target) | Out-Null; continue }
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $false)
        }
    } finally { $zip.Dispose() }
}
if ($InitializeBaseline) {
    if ((Test-Path -LiteralPath $baseZip) -or (Test-Path -LiteralPath $baseInfo)) { throw 'Baseline already exists; never overwrite a released baseline' }
    [IO.Directory]::CreateDirectory($baseDir) | Out-Null
    $zip = [IO.Compression.ZipFile]::Open($baseZip, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $files = @('settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','app/build.gradle.kts') | ForEach-Object { Get-Item -LiteralPath (Join-Path $project $_) }
        $files += Get-ChildItem -LiteralPath (Join-Path $project 'app/src/main'),(Join-Path $project 'gradle/wrapper') -File -Recurse
        foreach ($file in $files) {
            $relative = [IO.Path]::GetRelativePath($project, $file.FullName).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $relative) | Out-Null
        }
    } finally { $zip.Dispose() }
    @{baseId=$baseId; sha256=(Get-FileHash -LiteralPath $baseZip -Algorithm SHA256).Hash.ToLowerInvariant()} | ConvertTo-Json | Set-Content -LiteralPath $baseInfo -Encoding utf8
    Write-Output "Baseline saved: $baseZip"
    exit 0
}
if (!$Request) { throw 'Pass -Request path/to/signalfeed-rebuild.zip' }
$info = Get-Content -LiteralPath $baseInfo -Raw | ConvertFrom-Json
if ($info.baseId -ne $baseId -or (Get-FileHash -LiteralPath $baseZip -Algorithm SHA256).Hash -ne $info.sha256) { throw 'Baseline integrity check failed' }
$run = Join-Path $project ('mod-builds/' + [Guid]::NewGuid().ToString('N'))
$inputDir = Join-Path $run 'request'
$work = Join-Path $run 'source'
Expand-Safe ([IO.Path]::GetFullPath($Request)) $inputDir
$plan = Get-Content -LiteralPath (Join-Path $inputDir 'request.json') -Raw | ConvertFrom-Json
if ($plan.formatVersion -ne 1 -or $plan.baseId -ne $baseId) { throw 'Request baseline mismatch' }
if (@($plan.mods).Count -gt 32) { throw 'At most 32 code mods per build' }
if (@($plan.mods).Count -gt 0 -and !$AllowCodeMods) { throw 'Code mods execute with app/build permissions. Review the packages, then pass -AllowCodeMods.' }
Expand-Safe $baseZip $work
$claims = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$ids = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$active = @()
foreach ($requested in $plan.mods) {
    $sha = [string]$requested.sha256
    if ($sha -notmatch '^[a-f0-9]{64}$') { throw 'Invalid package checksum' }
    $archive = Join-Path $inputDir "packages/$sha.zip"
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $sha) { throw 'Package checksum mismatch' }
    $packDir = Join-Path $run "packages/$sha"
    Expand-Safe $archive $packDir
    $pack = Get-Content -LiteralPath (Join-Path $packDir 'mod.json') -Raw | ConvertFrom-Json
    if ($pack.formatVersion -ne 2 -or $pack.type -ne 'source-patch' -or $pack.baseId -ne $baseId -or $pack.id -notmatch '^[a-zA-Z0-9_-]{1,64}$' -or !$ids.Add($pack.id)) { throw 'Invalid or duplicate code mod' }
    if (@($pack.files).Count -notin 1..256) { throw 'Invalid patch file count' }
    foreach ($change in $pack.files) {
        $path = [string]$change.path
        if (!($path.StartsWith('app/src/main/') -or $path -eq 'app/build.gradle.kts')) { throw "Unsupported patch target: $path" }
        if ($path.StartsWith('app/src/main/assets/code-mod')) { throw 'Build manifest and package archive paths are reserved' }
        if (!$claims.Add($path)) { throw "Mod conflict: multiple packages modify $path" }
        $target = Resolve-Child $work $path
        if (Test-Path -LiteralPath $target -PathType Leaf) {
            if (!$change.beforeSha256 -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $change.beforeSha256) { throw "Baseline file mismatch: $path" }
        } elseif ($change.beforeSha256) { throw "Expected file is missing: $path" }
        switch ($change.action) {
            'replace' {
                $source = Resolve-Child $packDir ("files/" + $path)
                [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
                Copy-Item -LiteralPath $source -Destination $target -Force
            }
            'delete' {
                if (!(Test-Path -LiteralPath $target -PathType Leaf)) { throw "Cannot delete missing file: $path" }
                Remove-Item -LiteralPath $target
            }
            default { throw 'Unsupported patch action' }
        }
    }
    $pack | Add-Member -NotePropertyName sha256 -NotePropertyValue $sha -Force
    $active += $pack
    $assets = Join-Path $work 'app/src/main/assets/code-mod-packages'
    [IO.Directory]::CreateDirectory($assets) | Out-Null
    Copy-Item -LiteralPath $archive -Destination (Join-Path $assets "$sha.zip")
}
$assetRoot = Join-Path $work 'app/src/main/assets'
[IO.Directory]::CreateDirectory($assetRoot) | Out-Null
@{baseId=$baseId;buildId=[Guid]::NewGuid().ToString('N');mods=@($active)} | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $assetRoot 'code-mods.json') -Encoding utf8
Copy-Item -LiteralPath (Join-Path $project 'local.properties') -Destination (Join-Path $work 'local.properties')
if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle' }
Push-Location $work
try {
    & .\gradlew.bat :app:assembleDebug --no-daemon "-PapiBaseUrl=$ApiBaseUrl"
    if ($LASTEXITCODE -ne 0) { throw 'Build failed; installed app is unchanged' }
} finally { Pop-Location }
$apk = Join-Path $work 'app/build/outputs/apk/debug/app-debug.apk'
$output = Join-Path $run 'signalfeed-rebuilt.apk'
Copy-Item -LiteralPath $apk -Destination $output
Copy-Item -LiteralPath (Join-Path $assetRoot 'code-mods.json') -Destination (Join-Path $run 'installed-mods.json')
Write-Output "Built from clean baseline: $output"
if ($Install) {
    $sdk = ((Get-Content -LiteralPath (Join-Path $project 'local.properties') | Where-Object { $_ -match '^sdk.dir=' }) -replace '^sdk.dir=','').Replace('\\','\').Replace('\:',':')
    $aapt = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Filter aapt.exe -Recurse | Select-Object -Last 1
    $badging = & $aapt.FullName dump badging $output
    if ($LASTEXITCODE -ne 0 -or !($badging -match "package: name='cc.ccwu.signalfeed'")) { throw 'APK identity mismatch; not installed' }
    & (Join-Path $sdk 'platform-tools/adb.exe') install -r $output
    if ($LASTEXITCODE -ne 0) { throw 'Installation failed; do not uninstall the app or clear its data' }
}
