[CmdletBinding()]
param(
    [ValidatePattern('^\d+(\.\d+){0,3}$')]
    [string]$AppVersion = '1.0.0'
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$targetDirectory = Join-Path $projectRoot 'target'
$packageInput = Join-Path $targetDirectory 'jpackage-input'
$outputDirectory = Join-Path $targetDirectory 'windows'
$iconPath = Join-Path $projectRoot 'icons\PerSonal_Logo.ico'

foreach ($command in @('mvn', 'jpackage')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command '$command' was not found. Install Maven and a JDK containing jpackage, then add them to PATH."
    }
}

Push-Location $projectRoot
try {
    & mvn -DskipTests clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }

    $shadedJar = Get-ChildItem -LiteralPath $targetDirectory -Filter '*-shaded.jar' |
        Select-Object -First 1
    if (-not $shadedJar) {
        throw 'The shaded application JAR was not produced.'
    }

    New-Item -ItemType Directory -Path $packageInput -Force | Out-Null
    Copy-Item -LiteralPath $shadedJar.FullName -Destination $packageInput
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

    $jpackageArguments = @(
        '--type', 'app-image',
        '--name', 'BitXDisplayApp',
        '--app-version', $AppVersion,
        '--vendor', 'PerSonal',
        '--description', 'Live BitX display for Bitwig Studio',
        '--input', $packageInput,
        '--main-jar', $shadedJar.Name,
        '--main-class', 'com.personal.Launcher',
        '--add-modules', 'java.base,java.desktop,jdk.jfr,jdk.unsupported',
        '--dest', $outputDirectory
    )

    if (Test-Path -LiteralPath $iconPath) {
        $jpackageArguments += @('--icon', $iconPath)
    } else {
        Write-Warning 'icons\PerSonal_Logo.ico was not found; jpackage will use its default Windows icon.'
    }

    & jpackage @jpackageArguments
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    $launcher = Join-Path $outputDirectory 'BitXDisplayApp\BitXDisplayApp.exe'
    if (-not (Test-Path -LiteralPath $launcher)) {
        throw "Packaging completed without producing the expected launcher: $launcher"
    }

    Write-Host "Windows application image created at: $launcher"
} finally {
    Pop-Location
}
