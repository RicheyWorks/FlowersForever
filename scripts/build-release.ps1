#Requires -Version 7.0
param([switch]$SkipTests, [switch]$SkipMsi)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$pom = [xml](Get-Content -LiteralPath (Join-Path $project 'pom.xml') -Raw)
$version = $pom.project.version
if ($version -like '*-SNAPSHOT') {
    throw "Refusing to package snapshot version '$version'. Set the exact stable POM version first."
}
$target = Join-Path $project 'target'
$dist = Join-Path $project 'dist'
$staging = Join-Path $target 'jpackage-input'
$aiLauncher = Join-Path $project 'packaging\FlowersForeverAI-windows.properties'

Push-Location $project
try {
    if ($SkipTests) { & mvn -B package -DskipTests } else { & mvn -B verify -Pquality }
    if ($LASTEXITCODE -ne 0) { throw 'Maven release verification failed.' }
    $jar = Join-Path $target "flowerfarm-manager-$version.jar"
    $thirdParty = Join-Path $target 'generated-resources\licenses\THIRD-PARTY.txt'
    foreach ($required in @($jar, (Join-Path $target 'bom.json'),
            (Join-Path $target 'bom.xml'), $thirdParty, $aiLauncher)) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing release artifact: $required" }
    }
    $resolvedTarget = [IO.Path]::GetFullPath($target).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $resolvedStaging = [IO.Path]::GetFullPath($staging)
    if (-not $resolvedStaging.StartsWith($resolvedTarget + [IO.Path]::DirectorySeparatorChar)) {
        throw 'Refusing to clean staging outside the build directory.'
    }
    if (Test-Path -LiteralPath $resolvedStaging) { Remove-Item -LiteralPath $resolvedStaging -Recurse -Force }
    New-Item -ItemType Directory -Path $staging | Out-Null
    $resolvedProject = [IO.Path]::GetFullPath($project).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $resolvedDist = [IO.Path]::GetFullPath($dist)
    if (-not $resolvedDist.StartsWith($resolvedProject + [IO.Path]::DirectorySeparatorChar)) {
        throw 'Refusing to clean a release directory outside the project.'
    }
    if (Test-Path -LiteralPath $resolvedDist) {
        Remove-Item -LiteralPath $resolvedDist -Recurse -Force
    }
    New-Item -ItemType Directory -Path $resolvedDist | Out-Null
    Copy-Item -LiteralPath $jar -Destination $staging

    $appImage = Join-Path $dist 'FlowersForever'
    if (Test-Path -LiteralPath $appImage) {
        $resolvedDist = [IO.Path]::GetFullPath($dist).TrimEnd([IO.Path]::DirectorySeparatorChar)
        $resolvedImage = [IO.Path]::GetFullPath($appImage)
        if (-not $resolvedImage.StartsWith($resolvedDist + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Refusing to replace an app image outside the release directory.'
        }
        Remove-Item -LiteralPath $resolvedImage -Recurse -Force
    }

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
    $appImageZip = Join-Path $dist "FlowersForever-$version-windows-app-image.zip"
    if (Test-Path -LiteralPath $appImageZip) { Remove-Item -LiteralPath $appImageZip -Force }
    Compress-Archive -LiteralPath $appImage -DestinationPath $appImageZip -CompressionLevel Optimal
    if (-not $SkipMsi) {
        & jpackage --type msi --name FlowersForever --app-version $version --vendor RicheyWorks `
            --description 'Flower farm operations and knowledge, kept in one place' `
            --input $staging --main-jar ([IO.Path]::GetFileName($jar)) `
            --add-launcher "FlowersForeverAI=$aiLauncher" `
            --java-options '-Djava.awt.headless=false' --java-options '-Dflowerfarm.packaged=true' `
            --win-menu --win-shortcut --win-dir-chooser --dest $dist
        if ($LASTEXITCODE -ne 0) { throw 'jpackage MSI build failed.' }
    }
    Copy-Item -LiteralPath $jar -Destination $dist -Force
    Copy-Item -LiteralPath (Join-Path $target 'bom.json') -Destination $dist -Force
    Copy-Item -LiteralPath (Join-Path $target 'bom.xml') -Destination $dist -Force
    Copy-Item -LiteralPath $thirdParty -Destination $dist -Force
    $checksumManifest = Join-Path $dist 'SHA256SUMS.txt'
    if (Test-Path -LiteralPath $checksumManifest) { Remove-Item -LiteralPath $checksumManifest -Force }
    Get-ChildItem -LiteralPath $dist -File | Where-Object Name -ne 'SHA256SUMS.txt' | ForEach-Object {
        $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        "$($hash.Hash.ToLowerInvariant())  $($_.Name)"
    } | Set-Content -LiteralPath $checksumManifest -Encoding ascii
    Get-ChildItem -LiteralPath $dist | Select-Object FullName, Length
} finally {
    Pop-Location
}
