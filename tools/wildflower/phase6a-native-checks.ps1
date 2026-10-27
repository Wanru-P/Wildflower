param(
    [string]$ProjectRoot = 'C:\Users\Admin\Documents\ChatGPT\Wildflower',
    [string]$EvidenceDirectory = 'docs\wildflower\evidence\phase6a\permissions'
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$evidence = Join-Path (Resolve-Path -LiteralPath $ProjectRoot).Path $EvidenceDirectory
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
function Nodes {
    # am start may return before Compose has an accessibility root. Never read
    # yesterday's XML after a failed dump and tap coordinates from a stale page.
    $dumpOutput = & $adb shell uiautomator dump /sdcard/wildflower-phase6a-native.xml 2>&1
    if ($LASTEXITCODE -ne 0 -or ($dumpOutput -join '') -match 'ERROR:|null root') {
        throw 'Fresh native hierarchy unavailable; retry without using stale XML.'
    }
    [xml]$tree = (& $adb shell cat /sdcard/wildflower-phase6a-native.xml) -join ''
    return $tree.SelectNodes('//node')
}
function WaitNode([string]$attribute, [string]$value) {
    for($attempt = 0; $attempt -lt 12; $attempt++) {
        try { $match = Nodes | Where-Object { $actual = $_.GetAttribute($attribute); $actual -eq $value -or ($attribute -eq 'resource-id' -and $actual.EndsWith($value)) } | Select-Object -First 1; if($match) { return $match } } catch { }
        Start-Sleep -Milliseconds 300
    }
    throw "Native UI not found: $attribute=$value"
}
function Tap($node) {
    $numbers = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $adb shell input tap ([int](($numbers[0]+$numbers[2])/2)) ([int](($numbers[1]+$numbers[3])/2))
}
function Screenshot([string]$name) {
    & $adb shell screencap -p /sdcard/Download/wildflower-phase6a-permission.png
    & $adb pull /sdcard/Download/wildflower-phase6a-permission.png (Join-Path $evidence "$name.png") | Out-Null
}
# Dedicated Phase6A emulator only. Revokes one permission, never clears app data.
$avd = (& $adb shell getprop ro.boot.qemu.avd_name).Trim()
if($avd -ne 'Wildflower_NatureAI_API35') { throw "Wrong AVD: $avd" }
& $adb shell am force-stop com.floralens
& $adb shell pm revoke com.floralens android.permission.CAMERA
& $adb shell pm clear-permission-flags com.floralens android.permission.CAMERA user-set user-fixed
& $adb shell am start -W -n com.floralens/.MainActivity | Out-Null
Tap (WaitNode 'text' '识别')
WaitNode 'resource-id' ':id/permission_deny_button' | Out-Null
Screenshot '01-first-request'
Tap (WaitNode 'resource-id' ':id/permission_deny_button')
WaitNode 'text' '允许使用相机' | Out-Null
Screenshot '02-denied'
Tap (WaitNode 'text' '允许使用相机')
Tap (WaitNode 'resource-id' ':id/permission_allow_foreground_only_button')
WaitNode 'text' '离线 · 本机识别' | Out-Null
Start-Sleep -Milliseconds 2500 # permission callback precedes CameraX's first real frame
Screenshot '03-regranted'
& $adb shell input tap 540 1000
Start-Sleep -Milliseconds 200
Screenshot '04-tap-focus'
Write-Output 'PASS: native first request / denial / retry grant / camera return. Emulator tap focus only; physical AF requires a real device.'
