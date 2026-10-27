$ErrorActionPreference = 'Stop'
# Run interactively in the user's PowerShell. Only Windows DPAPI ciphertext is persisted, outside Git.
$storeFile = 'C:\Users\Admin\.android\release\wildflower-release.jks'
if (-not (Test-Path -LiteralPath $storeFile -PathType Leaf)) { throw 'Original release keystore not found; do not create a replacement.' }
$storePassword = Read-Host 'Original release keystore password (input hidden)' -AsSecureString
$same = Read-Host 'Use the same password for the key? [Y/n]'
$keyPassword = if ($same -eq 'n') { Read-Host 'Original release key password (input hidden)' -AsSecureString } else { $storePassword }
$settings = @{
    storeFile = $storeFile
    alias = 'wildflower_release'
    storePassword = ConvertFrom-SecureString $storePassword
    keyPassword = ConvertFrom-SecureString $keyPassword
}
$folder = Join-Path $env:LOCALAPPDATA 'Wildflower\Signing'
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$settings | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $folder 'release.dpapi.json') -Encoding UTF8
Write-Host 'Original signing credentials saved using Windows DPAPI, outside the project. No password was printed.'
