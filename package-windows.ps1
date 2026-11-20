param([string]$Destination = 'dist')
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path 'target/docforge-1.0.0.jar')) { throw 'Run mvn clean verify first' }
    $inputDir = Join-Path $PSScriptRoot 'target/package-input'
    New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
    Copy-Item -LiteralPath 'target/docforge-1.0.0.jar' -Destination $inputDir
    & "$env:JAVA_HOME/bin/jpackage.exe" --type app-image --input $inputDir --dest $Destination --name DocForge --main-jar docforge-1.0.0.jar --app-version 1.0.0 --vendor Fr1bilder --java-options '-Dfile.encoding=UTF-8'
    if ($LASTEXITCODE -ne 0) { throw 'Windows packaging failed' }
    Copy-Item -LiteralPath LICENSE,README.md,README.en.md -Destination (Join-Path $Destination 'DocForge')
    Compress-Archive -Path (Join-Path $Destination 'DocForge') -DestinationPath (Join-Path $Destination 'DocForge-1.0.0-windows-x64.zip')
} finally { Pop-Location }
