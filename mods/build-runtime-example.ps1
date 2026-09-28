param(
    [Parameter(Mandatory=$true)][string]$Output,
    [string]$Sdk = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { '' })
)
if (!$Sdk) { throw '请传入 -Sdk 指定 Android SDK 路径' }
$here = $PSScriptRoot
& (Join-Path $here 'api1/build-module.ps1') `
    -Manifest (Join-Path $here 'runtime-example/mod.json') `
    -Source (Join-Path $here 'runtime-example/src') `
    -Output $Output -Sdk $Sdk
