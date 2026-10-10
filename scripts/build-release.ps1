#Requires -Version 7.0
param([switch]$SkipTests, [switch]$SkipMsi)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$pom = [xml](Get-Content -LiteralPath (Join-Path $project 'pom.xml') -Raw)
$version = $pom.project.version
if ($version -like '*-SNAPSHOT') {
    throw "Refusing to package snapshot version '$version'. Set the exact stable POM version first."
}
$buildId = "$version-$([Guid]::NewGuid().ToString('N'))"
# Each invocation owns fresh output. Existing evidence and native runtimes may
# still be in use, so packaging must never clean or replace shared directories.
$target = Join-Path $project "target\release-$buildId"
$dist = Join-Path $project "dist\release-$buildId"
$staging = Join-Path $target 'jpackage-input'
$aiLauncher = Join-Path $project 'packaging\FlowersForeverAI-windows.properties'

Push-Location $project
try {
    if ($SkipTests) {
        & mvn -B package -DskipTests "-Dflowerfarm.build.directory=$target"
    } else {
        & mvn -B verify -Pquality "-Dflowerfarm.build.directory=$target"
    }
    if ($LASTEXITCODE -ne 0) { throw 'Maven release verification failed.' }
    $jar = Join-Path $target "flowerfarm-manager-$version.jar"
    $thirdParty = Join-Path $target 'generated-resources\licenses\THIRD-PARTY.txt'
    foreach ($required in @($jar, (Join-Path $target 'bom.json'),
            (Join-Path $target 'bom.xml'), $thirdParty, $aiLauncher)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing release artifact: $required" }
    }
    New-Item -ItemType Directory -Path $staging | Out-Null
    New-Item -ItemType Directory -Path $dist | Out-Null
    Copy-Item -LiteralPath $jar -Destination $staging

    $appImage = Join-Path $dist 'FlowersForever'

    & jpackage --type app-image --name FlowersForever --app-version $version --vendor RicheyWorks `
        --description 'Flower farm operations and knowledge, kept in one place' `
        --input $staging --main-jar ([IO.Path]::GetFileName($jar)) `
        --add-launcher "FlowersForeverAI=$aiLauncher" `
        --java-options '-Djava.awt.headless=false' --java-options '-Dflowerfarm.packaged=true' --dest $dist
    if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed.' }
    $aiExecutable = Join-Path $appImage 'FlowersForeverAI.exe'
    if (-not (Test-Path -LiteralPath $aiExecutable -PathType Leaf)) {
        throw "jpackage did not create the AI launcher: $aiExecutable"
    }
    $smokeData = Join-Path $target ('release-mcp-smoke-' + [Guid]::NewGuid().ToString('N'))
    & (Join-Path $PSScriptRoot 'test-mcp-launcher.ps1') -Launcher $aiExecutable -DataDir $smokeData
    $desktopExecutable = Join-Path $appImage 'FlowersForever.exe'
    & (Join-Path $PSScriptRoot 'test-desktop-launcher.ps1') -Launcher $desktopExecutable -DataDir $smokeData
    $appImageZip = Join-Path $dist "FlowersForever-$version-windows-app-image.zip"
    Compress-Archive -LiteralPath $appImage -DestinationPath $appImageZip -CompressionLevel Optimal
    if (-not $SkipMsi) {
        & jpackage --type msi --name FlowersForever --app-version $version --vendor RicheyWorks `
            --description 'Flower farm operations and knowledge, kept in one place' `
            --app-image $appImage `
            --win-menu --win-shortcut --win-dir-chooser --dest $dist
        if ($LASTEXITCODE -ne 0) { throw 'jpackage MSI build failed.' }
    }
    Copy-Item -LiteralPath $jar -Destination $dist -Force
    Copy-Item -LiteralPath (Join-Path $target 'bom.json') -Destination $dist -Force
    Copy-Item -LiteralPath (Join-Path $target 'bom.xml') -Destination $dist -Force
    Copy-Item -LiteralPath $thirdParty -Destination $dist -Force
    $checksumManifest = Join-Path $dist 'SHA256SUMS.txt'
    Get-ChildItem -LiteralPath $dist -File | Where-Object Name -ne 'SHA256SUMS.txt' | ForEach-Object {
        $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        "$($hash.Hash.ToLowerInvariant())  $($_.Name)"
    } | Set-Content -LiteralPath $checksumManifest -Encoding ascii
    Get-ChildItem -LiteralPath $dist | Select-Object FullName, Length
} finally {
    Pop-Location
}
