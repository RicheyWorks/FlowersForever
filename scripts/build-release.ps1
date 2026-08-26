param([switch]$SkipTests, [switch]$SkipMsi)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$pom = [xml](Get-Content -LiteralPath (Join-Path $project 'pom.xml') -Raw)
$version = $pom.project.version
$target = Join-Path $project 'target'
$dist = Join-Path $project 'dist'
$staging = Join-Path $target 'jpackage-input'

Push-Location $project
try {
    if ($SkipTests) { & mvn -B package -DskipTests } else { & mvn -B verify -Pquality }
    if ($LASTEXITCODE -ne 0) { throw 'Maven release verification failed.' }
    $jar = Join-Path $target "flowerfarm-manager-$version.jar"
    foreach ($required in @($jar, (Join-Path $target 'bom.json'), (Join-Path $target 'bom.xml'))) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing release artifact: $required" }
    }
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
    New-Item -ItemType Directory -Path $staging | Out-Null
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
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
        --description 'Flower Farm Manager' --input $staging --main-jar ([IO.Path]::GetFileName($jar)) `
        --java-options '-Djava.awt.headless=false' --java-options '-Dflowerfarm.packaged=true' --dest $dist
    if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed.' }
    $appImageZip = Join-Path $dist "FlowersForever-$version-windows-app-image.zip"
    if (Test-Path -LiteralPath $appImageZip) { Remove-Item -LiteralPath $appImageZip -Force }
    Compress-Archive -LiteralPath $appImage -DestinationPath $appImageZip -CompressionLevel Optimal
    if (-not $SkipMsi) {
        & jpackage --type msi --name FlowersForever --app-version $version --vendor RicheyWorks `
            --description 'Flower Farm Manager' --input $staging --main-jar ([IO.Path]::GetFileName($jar)) `
            --java-options '-Djava.awt.headless=false' --java-options '-Dflowerfarm.packaged=true' `
            --win-menu --win-shortcut --win-dir-chooser --dest $dist
        if ($LASTEXITCODE -ne 0) { throw 'jpackage MSI build failed.' }
    }
    Copy-Item -LiteralPath $jar -Destination $dist -Force
    Copy-Item -LiteralPath (Join-Path $target 'bom.json') -Destination $dist -Force
    Copy-Item -LiteralPath (Join-Path $target 'bom.xml') -Destination $dist -Force
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
