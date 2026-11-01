<#
.SYNOPSIS
  Turns the Windows uber jar into a self-contained app folder with
  OneClickCopy.exe and a trimmed Java runtime, using jpackage.

.EXAMPLE
  .\package-windows.ps1 -Jar OneClickCopy-2.3.0.jar -Jdk C:\jdk-17 -Out .\dist
#>
param(
    [Parameter(Mandatory)] [string] $Jar,
    [Parameter(Mandatory)] [string] $Jdk,
    [string] $Out = (Join-Path $PSScriptRoot 'dist'),
    [string] $Version = '2.3.0'
)
$ErrorActionPreference = 'Stop'

$jarFile = Get-Item $Jar
$staging = Join-Path ([IO.Path]::GetTempPath()) "oneclickcopy-jpackage-input"
Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $staging | Out-Null
Copy-Item $jarFile.FullName (Join-Path $staging 'OneClickCopy.jar')

Remove-Item (Join-Path $Out 'OneClickCopy') -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $Out -Force | Out-Null

# Modules the app needs: AWT/Swing for the window, TLS (jdk.crypto.ec) and
# naming for OkHttp, jdk.unsupported for Skiko and JNA.
$modules = 'java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.accessibility,jdk.zipfs'

& (Join-Path $Jdk 'bin\jpackage.exe') `
    --type app-image `
    --name OneClickCopy `
    --app-version $Version `
    --vendor OneClickCopy `
    --description 'One-click text copying with per-item tracking' `
    --icon (Join-Path $PSScriptRoot 'OneClickCopy.ico') `
    --input $staging `
    --main-jar OneClickCopy.jar `
    --main-class com.oneclickcopy.desktop.MainKt `
    --add-modules $modules `
    --jlink-options '--strip-debug --no-header-files --no-man-pages --compress=2' `
    --java-options '-Dfile.encoding=UTF-8' `
    --dest $Out
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

Remove-Item $staging -Recurse -Force
Write-Host "Packaged to $(Join-Path $Out 'OneClickCopy')"
