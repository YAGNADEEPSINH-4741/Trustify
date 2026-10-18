# Trustify - Device Installer Script
$adb = "C:\Users\YAGNADEEPSINH\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "D:\spam\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    $apk = "D:\spam\app\build\outputs\apk\release\app-release.apk"
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Trustify - APK Device Installer        " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if (-not (Test-Path $apk)) {
    Write-Host "[ERROR] APK not found at: $apk" -ForegroundColor Red
    exit 1
}

$apkItem = Get-Item $apk
Write-Host "[INFO] Target APK: $apk" -ForegroundColor Green
Write-Host "[INFO] Size: $($apkItem.Length) bytes ($([math]::Round($apkItem.Length/1MB, 2)) MB)" -ForegroundColor Green
Write-Host "[INFO] Last Modified: $($apkItem.LastWriteTime)" -ForegroundColor Green

Write-Host "`nChecking for connected Android devices via ADB..." -ForegroundColor Yellow
$devices = & $adb devices | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" }

if (-not $devices) {
    Write-Host "[WARNING] No device is currently recognized by ADB." -ForegroundColor Yellow
    Write-Host "Please ensure your phone is connected and USB Debugging is ON." -ForegroundColor White
    exit 1
}

$deviceId = ($devices[0] -split "\s+")[0]
Write-Host "`n[SUCCESS] Connected Device Found: $deviceId" -ForegroundColor Green

$model = & $adb -s $deviceId shell getprop ro.product.model
$androidVer = & $adb -s $deviceId shell getprop ro.build.version.release
Write-Host "[INFO] Device Model: $model" -ForegroundColor Cyan
Write-Host "[INFO] Android Version: $androidVer" -ForegroundColor Cyan

Write-Host "`nInstalling updated Trustify APK..." -ForegroundColor Yellow
$installOutput = & $adb -s $deviceId install -r $apk
Write-Host $installOutput

if ($installOutput -match "Success") {
    Write-Host "`n[SUCCESS] Installation succeeded!" -ForegroundColor Green
    Write-Host "Launching Trustify..." -ForegroundColor Yellow
    & $adb -s $deviceId shell am start -n com.safecallai.app/.MainActivity
    Write-Host "[DONE] Application launched." -ForegroundColor Green
} else {
    Write-Host "`n[WARNING] Install returned: $installOutput" -ForegroundColor Red
    if ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        Write-Host "Signing key mismatch detected. Reinstalling with clean install..." -ForegroundColor Yellow
        & $adb -s $deviceId uninstall com.safecallai.app
        & $adb -s $deviceId install $apk
        & $adb -s $deviceId shell am start -n com.safecallai.app/.MainActivity
    }
}
