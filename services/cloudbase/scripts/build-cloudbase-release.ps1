param([Parameter(Mandatory=$true)][string]$BaseUrl, [switch]$UseRecentVerification)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$protectedSigning = Join-Path $env:LOCALAPPDATA 'Wildflower\Signing\release.dpapi.json'
if (-not $env:WILDFLOWER_RELEASE_STORE_PASSWORD -and (Test-Path -LiteralPath $protectedSigning -PathType Leaf)) {
    $saved = Get-Content -LiteralPath $protectedSigning -Raw | ConvertFrom-Json
    $env:WILDFLOWER_RELEASE_STORE_FILE = $saved.storeFile
    $env:WILDFLOWER_RELEASE_KEY_ALIAS = $saved.alias
    $env:WILDFLOWER_RELEASE_STORE_PASSWORD = ([pscredential]::new('unused', (ConvertTo-SecureString $saved.storePassword))).GetNetworkCredential().Password
    $env:WILDFLOWER_RELEASE_KEY_PASSWORD = ([pscredential]::new('unused', (ConvertTo-SecureString $saved.keyPassword))).GetNetworkCredential().Password
}
if ($BaseUrl -notmatch '^https://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/[A-Za-z0-9_~-]+)*$' -or $BaseUrl -match '/v1/(chat|nature|session)$') {
    throw 'Supply the HTTPS service base, including /wildflower-ai; not a /v1 endpoint.'
}
foreach ($name in @('WILDFLOWER_RELEASE_STORE_FILE','WILDFLOWER_RELEASE_STORE_PASSWORD','WILDFLOWER_RELEASE_KEY_ALIAS','WILDFLOWER_RELEASE_KEY_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "Existing release signing configuration required: $name" }
}
Push-Location -LiteralPath $projectRoot
try {
    if ($UseRecentVerification) {
        # Resume a failed Gradle build only after this run's successful real-provider gate.
        # Never substitute mock tests, a route-only probe, or a stale/different-service report.
        $report = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../verification/live_provider.json') -Raw | ConvertFrom-Json
        # PowerShell 7.5 parses JSON ISO timestamps as UTC DateTime objects;
        # reparsing their culture-formatted string would incorrectly apply +08:00.
        $verifiedAt = if ($report.checkedAt -is [DateTime]) { [DateTimeOffset]::new($report.checkedAt) } else { [DateTimeOffset]::Parse($report.checkedAt) }
        $age = [DateTimeOffset]::UtcNow - $verifiedAt
        $required = @('installation_session','chat_turn_1','chat_turn_2','chat_turn_3','chat_context_recalled','nature_generation')
        if (-not $report.passed -or $report.mode -ne 'live_provider' -or $report.baseUrl -ne $BaseUrl -or $report.generationRequests -ne 4 -or $age.TotalMinutes -lt 0 -or $age.TotalMinutes -gt 15) { throw 'A successful live-provider report from the last 15 minutes is required.' }
        foreach ($check in $required) { if (-not ($report.checks | Where-Object { $_.name -eq $check -and $_.passed })) { throw 'Incomplete live-provider evidence.' } }
        & node (Join-Path $PSScriptRoot 'verify-live.mjs') --base-url $BaseUrl --require-ready
        if ($LASTEXITCODE -ne 0) { throw 'Current CloudBase route probe failed.' }
        Write-Output 'Reusing this run''s successful four-call live verification; no additional model request.'
    } else {
        & node (Join-Path $PSScriptRoot 'verify-live.mjs') --base-url $BaseUrl --live
        if ($LASTEXITCODE -ne 0) { throw 'CloudBase real-provider checks failed. Release entry remains unchanged.' }
    }
    & .\gradlew.bat "-PnatureProxyOrigin=$BaseUrl" assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Signed Release build failed.' }
    $apk=Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    [pscustomobject]@{ Path=$apk; Bytes=(Get-Item -LiteralPath $apk).Length; SHA256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash } | ConvertTo-Json
} finally { Pop-Location }
