param(
    [string]$DataDir = $(if ($env:FLOWERFARM_DATA_DIR) { $env:FLOWERFARM_DATA_DIR } else { Join-Path $env:LOCALAPPDATA 'FlowersForever\data' }),
    [string]$Destination = $(Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'FlowersForever Backups')
)

$ErrorActionPreference = 'Stop'
$resolvedData = [IO.Path]::GetFullPath($DataDir)
if (-not (Test-Path -LiteralPath $resolvedData -PathType Container)) {
    throw "Flowers Forever data folder does not exist: $resolvedData"
}

$database = Join-Path $resolvedData 'flowerfarm.mv.db'
if (Test-Path -LiteralPath $database) {
    try {
        $handle = [IO.File]::Open($database, 'Open', 'Read', 'None')
        $handle.Dispose()
    } catch {
        throw 'Flowers Forever is using the database. Close the app before creating a backup.'
    }
}

New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$archive = Join-Path ([IO.Path]::GetFullPath($Destination)) "FlowersForever-$stamp.zip"
Compress-Archive -LiteralPath $resolvedData -DestinationPath $archive -CompressionLevel Optimal
$hash = Get-FileHash -LiteralPath $archive -Algorithm SHA256
"$($hash.Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($archive))" |
    Set-Content -LiteralPath "$archive.sha256" -Encoding ascii
Write-Output $archive
Write-Output "$archive.sha256"

