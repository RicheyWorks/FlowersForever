<#
.SYNOPSIS
  FlowersForever update release tool (signed update manifest).

.DESCRIPTION
  Runs com.flowerfarm.update.release.UpdateReleaseTool from the built release
  JAR. All commands and options pass through. See docs/AUTO_UPDATES.md.

.EXAMPLE
  pwsh scripts/update-release.ps1 keygen --out "$env:USERPROFILE\FlowersForever-signing\update-signing.key"
  pwsh scripts/update-release.ps1 add-release --manifest ..\FlowersForever-updates\manifest.unsigned.json `
      --private-key "$env:USERPROFILE\FlowersForever-signing\update-signing.key" --version 1.2.1 --channels owner `
      --minimum-version 1.2.0 --notes-file notes-1.2.1.txt `
      --package "windows-msi=dist\release-...\FlowersForever-1.2.1.msi@https://github.com/RicheyWorks/FlowersForever-updates/releases/download/v1.2.1/FlowersForever-1.2.1.msi"
  pwsh scripts/update-release.ps1 sign --manifest ..\FlowersForever-updates\manifest.unsigned.json `
      --private-key "$env:USERPROFILE\FlowersForever-signing\update-signing.key" --out ..\FlowersForever-updates\latest.json
  pwsh scripts/update-release.ps1 verify --signed ..\FlowersForever-updates\latest.json
#>
# Arguments pass straight through ($args), so tool options like --out are not parsed by PowerShell.
# Set FLOWERSFOREVER_JAR to choose a specific JAR; otherwise the newest built JAR is used.
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$Jar = $env:FLOWERSFOREVER_JAR
if (-not $Jar) {
    $candidates = @(Get-ChildItem -Path (Join-Path $project 'target'), (Join-Path $project 'dist') -Recurse `
            -Filter 'flowerfarm-manager-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*-plain.jar' -and $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending)
    if ($candidates.Count -eq 0) {
        throw 'No flowerfarm-manager JAR found. Run "mvn -B package -DskipTests" first, or set FLOWERSFOREVER_JAR.'
    }
    $Jar = $candidates[0].FullName
}
# Java writes notices such as "Picked up JAVA_TOOL_OPTIONS" to stderr; do not treat them as failures.
$ErrorActionPreference = 'Continue'
& java -cp $Jar '-Dloader.main=com.flowerfarm.update.release.UpdateReleaseTool' `
    org.springframework.boot.loader.launch.PropertiesLauncher @args
exit $LASTEXITCODE
