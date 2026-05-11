param(
    [string]$JavaPath = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe',
    [string]$DbUrl = 'jdbc:postgresql://localhost:5432/kbook_saas',
    [string]$DbUsername = 'postgres',
    [string]$DbPassword = 'postgres'
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot '..')
$envFile = Join-Path $repoRoot '.env'
$argsFile = Join-Path $scriptRoot 'target\khanabook-run.args'

if (-not (Test-Path $JavaPath)) {
    throw "JDK 21 not found at '$JavaPath'. Update -JavaPath to point to a local Java 21 installation."
}

if (-not (Test-Path $argsFile)) {
    throw "Missing launch args file: $argsFile. Build the project first so target\khanabook-run.args exists."
}

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^(?<key>[A-Z0-9_]+)=(?<value>.*)$') {
            Set-Item -Path "Env:$($Matches.key)" -Value $Matches.value
        }
    }
}

# Force the local database target so the launcher does not inherit the remote host
# from the shared .env file.
$env:DB_URL = $DbUrl
$env:DB_USERNAME = $DbUsername
$env:DB_PASSWORD = $DbPassword

Write-Host "Using Java: $JavaPath"
Write-Host "Using DB_URL: $env:DB_URL"
Write-Host "Using DB_USERNAME: $env:DB_USERNAME"
Write-Host "Launching KhanaBook SaaS with dev profile"

& $JavaPath "@$argsFile"
