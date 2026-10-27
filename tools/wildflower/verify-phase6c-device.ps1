param(
    [string]$ProjectRoot = 'C:\Users\Admin\Documents\ChatGPT\Wildflower',
    [switch]$SkipCold
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $ProjectRoot
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$evidence = Join-Path $ProjectRoot 'docs\wildflower\evidence\phase6c'
if ((& $adb shell getprop ro.boot.qemu.avd_name).Trim() -ne 'Wildflower_NatureAI_API35') {
    throw 'Use the dedicated API35 AVD only; no device data is cleared.'
}
& $adb install -r --no-incremental app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'Debug verification APK update failed; do not uninstall to bypass this.' }
& $adb install -r --no-incremental app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'Test APK update failed.' }

function Run-Check([string]$Name, [string[]]$Arguments) {
    $output = & $adb shell am instrument -w -r @Arguments com.floralens.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $output | Out-File -LiteralPath (Join-Path $evidence "$Name.txt") -Encoding utf8
    $passed = ($output -join "`n") -match 'OK \(\d+ tests?\)'
    if ($passed) { Write-Output "${Name}: PASS" } else { Write-Output "${Name}: FAIL_OR_BLOCKED (inspect raw result)" }
    return [bool]$passed
}
$journey = 'com.floralens.Phase2JourneyTest#photograph_collect_camera_picker_edit_cover_andRelaunch'
# This cannot inject an identification result. Retain ONLY this test's owned archive,
# force-stop the process, then separately verify persisted files before scoped cleanup.
if (-not $SkipCold) {
  $strict = Run-Check 'cold-seed-real-flow' @('-e','class',$journey,'-e','phase6aStrict','true','-e','keepPhase2Evidence','true')
  if ($strict -contains $true) {
    & $adb shell am force-stop com.floralens
    $cold = Run-Check 'cold-relaunch-verification' @('-e','class',$journey,'-e','verifyColdPhase2','true','-e','cleanupPhase6a','true')
    if ($cold -notcontains $true) { throw 'Cold verification failed; preserve evidence and do not clear archive data.' }
  } else {
    throw 'Strict real-model flow did not finish; do not claim or fabricate cold-start verification.'
  }
}

& ./tools/wildflower/phase6a-native-checks.ps1 -ProjectRoot $ProjectRoot -EvidenceDirectory 'docs\wildflower\evidence\phase6c\permissions' *> (Join-Path $evidence 'permissions.txt')
Write-Output 'permissions: completed; inspect result and screenshots'
$null = Run-Check 'plantnet-real-camera' @('-e','class',$journey,'-e','verifyOnlineCamera','true')
$null = Run-Check 'network-offline-fallback' @('-e','class',$journey,'-e','verifyNetworkFallback','true')

$maps = 'com.floralens.Phase6ACapabilitiesTest#locatedRoomPin_opensCorrectArchive_andActualNotificationPendingIntent_opensCare'
$originalProxy = (& $adb shell settings get global http_proxy).Trim()
try {
    # Reuse the already-running local proxy only for this dedicated AVD; never change host config.
    if (Get-NetTCPConnection -State Listen -LocalPort 12000 -ErrorAction SilentlyContinue) {
        & $adb shell settings put global http_proxy 10.0.2.2:12000 | Out-Null
    }
    $null = Run-Check 'google-map-marker-navigation' @('-e','class',$maps,'-e','verifyGoogleMaps','true')
    $null = Run-Check 'google-map-network-error' @('-e','class',$maps,'-e','verifyGoogleMapsFailure','true')
} finally {
    if ($originalProxy -eq 'null' -or [string]::IsNullOrWhiteSpace($originalProxy)) {
        & $adb shell settings delete global http_proxy | Out-Null
    } else {
        & $adb shell settings put global http_proxy $originalProxy | Out-Null
    }
}
# Capture the real system chooser without advertising the instrumentation-only
# receiver as a product sharing target. Its cross-UID read was already verified
# in the strict journey; always restore the test component after this capture.
try {
    & $adb shell run-as com.floralens.test pm disable com.floralens.test/com.floralens.CardReceiveActivity | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not hide the instrumentation-only sharing target; do not claim a clean chooser.' }
    $null = Run-Check 'presentation-screens-clean-chooser' @('-e','class','com.floralens.Phase6BProductStoryTest','-e','phase6cEvidence','true')
} finally {
    & $adb shell run-as com.floralens.test pm enable com.floralens.test/com.floralens.CardReceiveActivity | Out-Null
}
& $adb logcat -d -s Phase6AEvidence:I Phase2Evidence:I Phase6AOnline:I '*:S' *> (Join-Path $evidence 'real-flow-events.txt')
& $adb shell am force-stop com.floralens
& $adb shell am start -W -n com.floralens/.MainActivity *> (Join-Path $evidence 'final-debug-cold-launch.txt')
Write-Output 'Finished Debug verification only; unsigned Release is still not installable.'
