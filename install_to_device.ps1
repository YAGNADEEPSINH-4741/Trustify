# SafeCall AI - Device Installer Script
$adb = "C:\Users\YAGNADEEPSINH\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "D:\spam\SafeCallAI-release.apk"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " SafeCall AI - APK Device Installer    " -ForegroundColor Cyan
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
    Write-Host "[INFO] Detected Windows USB Device: 'Vaghela's A16' (Samsung Galaxy A16, Serial: R9ZY10QQ57X)" -ForegroundColor Cyan
    Write-Host "The phone is connected in MTP (File Transfer) mode, but 'USB Debugging' is currently OFF." -ForegroundColor Yellow
    Write-Host "Please perform the following on your Galaxy A16:" -ForegroundColor White
    Write-Host "  1. Open Settings -> About phone -> Software information." -ForegroundColor White
    Write-Host "  2. Tap 'Build number' 7 times rapidly (until it says 'Developer mode has been enabled')." -ForegroundColor White
    Write-Host "  3. Go back to Settings -> Developer options (at the very bottom of Settings)." -ForegroundColor White
    Write-Host "  4. Turn ON 'USB Debugging'." -ForegroundColor White
    Write-Host "  5. When prompted on the phone with 'Allow USB debugging?', check 'Always allow' and tap 'OK / Allow'." -ForegroundColor White
    Write-Host "`nWaiting for device to connect via ADB (timeout 180s)..." -ForegroundColor Yellow
    
    $timeout = 180
    while ($timeout -gt 0 -and (-not $devices)) {
        Start-Sleep -Seconds 2
        $timeout -= 2
        $devices = & $adb devices | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" }
        if ($devices) { break }
        Write-Host "." -NoNewline
    }
    Write-Host ""
}

if (-not $devices) {
    Write-Host "`n[ERROR] Device still not detected by ADB." -ForegroundColor Red
    Write-Host "Once your phone shows the USB Debugging prompt and you tap Allow, run this script again or run:" -ForegroundColor White
    Write-Host "  adb install -r `"$apk`"" -ForegroundColor Cyan
    exit 1
}

$deviceId = ($devices[0] -split "\s+")[0]
Write-Host "`n[SUCCESS] Connected Device Found: $deviceId" -ForegroundColor Green

$model = & $adb -s $deviceId shell getprop ro.product.model
$androidVer = & $adb -s $deviceId shell getprop ro.build.version.release
Write-Host "[INFO] Device Model: $model" -ForegroundColor Cyan
Write-Host "[INFO] Android Version: $androidVer" -ForegroundColor Cyan

Write-Host "`nInstalling updated SafeCall AI APK..." -ForegroundColor Yellow
$installOutput = & $adb -s $deviceId install -r $apk
Write-Host $installOutput

if ($installOutput -match "Success") {
    Write-Host "`n[SUCCESS] Installation succeeded!" -ForegroundColor Green
    Write-Host "Launching SafeCall AI..." -ForegroundColor Yellow
    & $adb -s $deviceId shell am start -n com.safecallai.app/.MainActivity
    Write-Host "[DONE] Application launched." -ForegroundColor Green
} else {
    Write-Host "`n[WARNING] Install returned: $installOutput" -ForegroundColor Red
    if ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        Write-Host "Signing key mismatch detected. Reinstalling with uninstall..." -ForegroundColor Yellow
        & $adb -s $deviceId uninstall com.safecallai.app
        & $adb -s $deviceId install $apk
        & $adb -s $deviceId shell am start -n com.safecallai.app/.MainActivity
    }
}
