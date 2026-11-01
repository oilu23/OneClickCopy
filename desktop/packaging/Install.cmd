@echo off
rem Installs OneClickCopy for the current user (no admin needed).
rem Runs install-windows.ps1 with the app folder that sits next to this file.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-windows.ps1" -AppImage "%~dp0OneClickCopy"
if errorlevel 1 (
    echo.
    echo Installation failed. See the message above.
    pause
    exit /b 1
)
echo.
echo Done. Start OneClickCopy from the Start menu or the desktop shortcut.
timeout /t 5 >nul
