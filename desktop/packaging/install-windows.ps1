<#
.SYNOPSIS
  Installs (or updates) OneClickCopy for the current user. No admin needed.

  Copies the app folder produced by package-windows.ps1 to
  %LOCALAPPDATA%\Programs\OneClickCopy, adds Start menu and desktop
  shortcuts, and registers an uninstaller under Settings > Apps.
  Documents in %APPDATA%\OneClickCopy are never touched.

.EXAMPLE
  .\install-windows.ps1 -AppImage .\dist\OneClickCopy
.EXAMPLE
  .\install-windows.ps1 -Uninstall
#>
param(
    [string] $AppImage = (Join-Path $PSScriptRoot 'dist\OneClickCopy'),
    [switch] $Uninstall,
    [switch] $NoDesktopShortcut
)
$ErrorActionPreference = 'Stop'

$name = 'OneClickCopy'
$installDir = Join-Path $env:LOCALAPPDATA "Programs\$name"
$exe = Join-Path $installDir "$name.exe"
$startMenuLink = Join-Path ([Environment]::GetFolderPath('Programs')) "$name.lnk"
$desktopLink = Join-Path ([Environment]::GetFolderPath('Desktop')) "$name.lnk"
$uninstallKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\$name"

function Stop-App {
    # Ask the window to close first so the app saves pending edits and flushes
    # its backup; the jpackage launcher is a windowless parent of the JVM.
    $procs = @(Get-Process -Name $name -ErrorAction SilentlyContinue)
    $procs | Where-Object { $_.MainWindowHandle -ne 0 } | ForEach-Object { $_.CloseMainWindow() | Out-Null }
    foreach ($p in $procs) {
        if (-not $p.WaitForExit(10000)) { $p.Kill() }
    }
}

if ($Uninstall) {
    Stop-App
    Remove-Item $startMenuLink, $desktopLink -Force -ErrorAction SilentlyContinue
    Remove-Item $uninstallKey -Recurse -Force -ErrorAction SilentlyContinue
    # The uninstaller runs from inside the install folder, so remove it
    # after this script exits.
    Start-Process -WindowStyle Hidden cmd.exe "/c timeout /t 2 >nul & rmdir /s /q `"$installDir`""
    Write-Host "$name uninstalled. Your documents remain in $env:APPDATA\$name."
    return
}

if (-not (Test-Path (Join-Path $AppImage "$name.exe"))) {
    throw "No $name.exe in '$AppImage'. Run package-windows.ps1 first."
}

Stop-App
if (Test-Path $installDir) { Remove-Item $installDir -Recurse -Force }
New-Item -ItemType Directory $installDir -Force | Out-Null
Copy-Item (Join-Path $AppImage '*') $installDir -Recurse -Force
Copy-Item $PSCommandPath (Join-Path $installDir 'uninstall.ps1') -Force

$shell = New-Object -ComObject WScript.Shell
$links = @($startMenuLink)
if (-not $NoDesktopShortcut) { $links += $desktopLink }
foreach ($path in $links) {
    $link = $shell.CreateShortcut($path)
    $link.TargetPath = $exe
    $link.WorkingDirectory = $installDir
    $link.IconLocation = "$exe,0"
    $link.Description = 'One-click text copying with per-item tracking'
    $link.Save()
}

$version = (Get-Item $exe).VersionInfo.ProductVersion
$size = [int]((Get-ChildItem $installDir -Recurse | Measure-Object Length -Sum).Sum / 1KB)
New-Item $uninstallKey -Force | Out-Null
$values = @{
    DisplayName     = $name
    DisplayIcon     = "$exe,0"
    DisplayVersion  = $version
    Publisher       = $name
    InstallLocation = $installDir
    UninstallString = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$installDir\uninstall.ps1`" -Uninstall"
    NoModify        = 1
    NoRepair        = 1
    EstimatedSize   = $size
}
foreach ($entry in $values.GetEnumerator()) {
    $type = if ($entry.Value -is [int]) { 'DWord' } else { 'String' }
    New-ItemProperty $uninstallKey -Name $entry.Key -Value $entry.Value -PropertyType $type -Force | Out-Null
}

Write-Host "$name $version installed to $installDir"
