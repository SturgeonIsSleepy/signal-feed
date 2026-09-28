param(
    [Parameter(Mandatory=$true)][string]$Manifest,
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][string]$Output,
    [string]$Sdk = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { '' })
)
$ErrorActionPreference = 'Stop'
$specRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$Sdk) { throw '请传入 -Sdk 指定 Android SDK 路径' }
$Sdk = [IO.Path]::GetFullPath($Sdk)
$platform = Get-ChildItem (Join-Path $Sdk 'platforms') -Directory | Where-Object Name -Match '^android-[0-9]+$' | Sort-Object { [int]($_.Name -replace '\D','') } -Descending | Select-Object -First 1
$buildTools = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'd8.bat') } | Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending | Select-Object -First 1
if (!$platform -or !$buildTools) { throw 'SDK 需安装 Android 平台与 Build Tools' }
$androidJar = Join-Path $platform.FullName 'android.jar'
$javac = Get-Command javac -ErrorAction SilentlyContinue
$jarCommand = Get-Command jar -ErrorAction SilentlyContinue
if (!$javac -or !$jarCommand) { throw '请安装并配置 JDK 17 或更新版本' }
$manifestPath = [IO.Path]::GetFullPath($Manifest)
$sourcePath = [IO.Path]::GetFullPath($Source)
$outputPath = [IO.Path]::GetFullPath($Output)
$metadata = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($metadata.formatVersion -ne 3 -or $metadata.apiVersion -ne 1) { throw '此工具只构建格式 3、API 1 手机模块' }
if ($metadata.entryClass -notmatch '^[A-Za-z_][A-Za-z0-9_.$]+$') { throw 'entryClass 格式错误' }
$work = Join-Path ([IO.Path]::GetTempPath()) ('signalfeed-mod-' + [guid]::NewGuid().ToString('N'))
$apiClasses = Join-Path $work 'api-classes'
$modClasses = Join-Path $work 'module-classes'
$dexOutput = Join-Path $work 'dex'
$zipRoot = Join-Path $work 'zip'
New-Item -ItemType Directory -Force $apiClasses,$modClasses,$dexOutput,$zipRoot | Out-Null
$apiSource = Join-Path $specRoot 'app/src/main/java/cc/ccwu/signalfeed/modapi/SignalMod.java'
& $javac.Source -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d $apiClasses $apiSource
if ($LASTEXITCODE -ne 0) { throw 'API 编译失败' }
$apiJar = Join-Path $work 'signalfeed-mod-api-1.jar'
& $jarCommand.Source --create --file $apiJar -C $apiClasses .
if ($LASTEXITCODE -ne 0) { throw 'API jar 打包失败' }
$sources = @(Get-ChildItem -LiteralPath $sourcePath -Filter '*.java' -File -Recurse | ForEach-Object FullName)
if ($sources.Count -eq 0) { throw '源码目录没有 Java 文件' }
& $javac.Source -encoding UTF-8 -source 8 -target 8 -classpath "$apiJar;$androidJar" -d $modClasses @sources
if ($LASTEXITCODE -ne 0) { throw '模块源码编译失败' }
$classes = @(Get-ChildItem -LiteralPath $modClasses -Filter '*.class' -File -Recurse | ForEach-Object FullName)
& (Join-Path $buildTools.FullName 'd8.bat') --lib $androidJar --classpath $apiJar --min-api 26 --output $dexOutput @classes
if ($LASTEXITCODE -ne 0) { throw 'DEX 转换失败' }
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $zipRoot 'mod.json')
Copy-Item -LiteralPath (Join-Path $dexOutput 'classes.dex') -Destination (Join-Path $zipRoot 'classes.dex')
if (Test-Path (Join-Path $PSScriptRoot 'assets')) { Copy-Item (Join-Path $PSScriptRoot 'assets') (Join-Path $zipRoot 'assets') -Recurse }
$libDir = Join-Path $PSScriptRoot 'lib'
if (Test-Path $libDir) { Copy-Item $libDir (Join-Path $zipRoot 'lib') -Recurse }
$outputParent = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force $outputParent | Out-Null
if (Test-Path -LiteralPath $outputPath) { Remove-Item -LiteralPath $outputPath -Force }
Compress-Archive -Path (Join-Path $zipRoot '*') -DestinationPath $outputPath
Write-Output "Module ZIP created: $outputPath"
Remove-Item -LiteralPath $work -Recurse -Force
