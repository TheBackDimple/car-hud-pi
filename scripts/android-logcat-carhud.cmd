@echo off
REM From repo root or anywhere — uses Android SDK platform-tools on PATH or default location.
REM 1) Plug in phone, enable USB debugging
REM 2) Settings → Pi host = auto (not an IP) to see PiDiscovery logs
REM 3) Run this, then tap Connect in Car HUD

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo adb not found at %ADB%
  echo Install Android SDK Platform-Tools or set ADB= path to adb.exe
  exit /b 1
)

echo Clearing log buffer...
"%ADB%" logcat -c

echo.
echo Only CarHudConn + CarHudPiDiscovery ERROR/WARN lines. Tap Connect now.
echo Press Ctrl+C to stop.
echo.

REM *:S silences everything; then only these tags (V = verbose, includes E/W/I/D for that tag)
"%ADB%" logcat -v time *:S CarHudConn:V CarHudPiDiscovery:V CarHud:V
