param([string]$Adb = 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe')
$ErrorActionPreference = 'Stop'
$deviceTask = 'emulator-5554'
$avdTask = (& $Adb -s $deviceTask emu avd name) -join "`n"
if ($avdTask -notmatch 'Wildflower_API35') { throw 'This script is restricted to the dedicated Phase 1 AVD.' }
$evidenceTask = Join-Path $PSScriptRoot '../docs/wildflower/evidence/phase1'
New-Item -ItemType Directory -Force $evidenceTask | Out-Null

function Wait-UiText([string]$Text) {
    for ($attemptTask=0; $attemptTask -lt 10; $attemptTask++) {
        $dumpTask = (& $Adb -s $deviceTask shell uiautomator dump /sdcard/wildflower-check.xml) -join "`n"
        if ($dumpTask -notmatch 'dumped to:') { Start-Sleep -Milliseconds 300; continue }
        [xml]$treeTask = (& $Adb -s $deviceTask shell cat /sdcard/wildflower-check.xml) -join "`n"
        $nodeTask = $treeTask.SelectSingleNode("//node[@text='$Text']")
        if ($null -ne $nodeTask) { return $nodeTask }
        Start-Sleep -Milliseconds 300
    }
    throw "Expected UI text was not found: $Text"
}
function Tap-UiText([string]$Text) {
    $nodeTask = Wait-UiText $Text
    $boundsTask = [regex]::Matches($nodeTask.bounds, '\d+') | ForEach-Object {[int]$_.Value}
    $xTask = [int](($boundsTask[0]+$boundsTask[2])/2)
    $yTask = [int](($boundsTask[1]+$boundsTask[3])/2)
    & $Adb -s $deviceTask shell input tap $xTask $yTask | Out-Null
}
function Save-Screen([string]$Name) {
    Start-Sleep -Milliseconds 600
    & $Adb -s $deviceTask shell screencap -p "/sdcard/Download/wildflower-phase1/$Name.png"
    & $Adb -s $deviceTask pull "/sdcard/Download/wildflower-phase1/$Name.png" (Join-Path $evidenceTask "$Name.png")
}

# Run after Phase1NavigationTest has completed onboarding in this dedicated installation.
try {
    & $Adb -s $deviceTask shell wm size 840x1680
    & $Adb -s $deviceTask shell wm density 420
    & $Adb -s $deviceTask shell am start -n com.floralens/.MainActivity | Out-Null
    Wait-UiText '发现自然，收藏四季。' | Out-Null
    Save-Screen 'explore-narrow'
    Tap-UiText '花园'
    Wait-UiText 'My Garden' | Out-Null
    Save-Screen 'garden-narrow'
    Tap-UiText '发现'
    & $Adb -s $deviceTask shell wm size 1920x2560
    & $Adb -s $deviceTask shell wm density 320
    Wait-UiText '发现自然，收藏四季。' | Out-Null
    Save-Screen 'explore-tablet'
    Tap-UiText '花园'
    Wait-UiText 'My Garden' | Out-Null
    Save-Screen 'garden-tablet'
} finally {
    & $Adb -s $deviceTask shell wm size reset
    & $Adb -s $deviceTask shell wm density reset
}

# Denial is a state check on the test installation only; restore permission and flags afterward.
try {
    & $Adb -s $deviceTask shell pm revoke com.floralens android.permission.CAMERA
    & $Adb -s $deviceTask shell pm set-permission-flags com.floralens android.permission.CAMERA user-set user-fixed
    & $Adb -s $deviceTask shell am start -n com.floralens/.MainActivity | Out-Null
    Tap-UiText '识别'
    Wait-UiText '开启镜头，发现自然' | Out-Null
    Save-Screen 'scan-permission-denied'
} finally {
    & $Adb -s $deviceTask shell pm clear-permission-flags com.floralens android.permission.CAMERA user-set user-fixed
    & $Adb -s $deviceTask shell pm grant com.floralens android.permission.CAMERA
}
Write-Output 'Responsive layout and camera-denial screenshots captured; device overrides restored.'
