param(
    [Parameter(Mandatory = $true)][string]$Archive,
    [string]$DataDir = $(if ($env:FLOWERFARM_DATA_DIR) { $env:FLOWERFARM_DATA_DIR } else { Join-Path $env:LOCALAPPDATA 'FlowersForever\data' })
)

$ErrorActionPreference = 'Stop'
$resolvedArchive = [IO.Path]::GetFullPath($Archive)
$resolvedData = [IO.Path]::GetFullPath($DataDir)
if (-not (Test-Path -LiteralPath $resolvedArchive -PathType Leaf)) {
    throw "Backup archive does not exist: $resolvedArchive"
}
$checksumFile = "$resolvedArchive.sha256"
if (-not (Test-Path -LiteralPath $checksumFile -PathType Leaf)) {
    throw "Checksum file is required: $checksumFile"
}
$expected = ((Get-Content -LiteralPath $checksumFile -Raw).Trim() -split '\s+')[0]
$actual = (Get-FileHash -LiteralPath $resolvedArchive -Algorithm SHA256).Hash
if ($actual -ne $expected) {
    throw 'Backup checksum does not match. The archive may be damaged or incomplete.'
}

$database = Join-Path $resolvedData 'flowerfarm.mv.db'
if (Test-Path -LiteralPath $database) {
    try {
        $handle = [IO.File]::Open($database, 'Open', 'Read', 'None')
        $handle.Dispose()
    } catch {
        throw 'Flowers Forever is using the database. Close the app before restoring.'
    }
}

$parent = Split-Path -Parent $resolvedData
New-Item -ItemType Directory -Path $parent -Force | Out-Null
$temporary = Join-Path $parent ('.restore-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
try {
    Expand-Archive -LiteralPath $resolvedArchive -DestinationPath $temporary
    $entries = @(Get-ChildItem -LiteralPath $temporary)
    $source = if ($entries.Count -eq 1 -and $entries[0].PSIsContainer) { $entries[0].FullName } else { $temporary }
    if (-not (Test-Path -LiteralPath (Join-Path $source 'flowerfarm.mv.db'))) {
        throw 'Backup does not contain a Flowers Forever H2 database.'
    }
    if (Test-Path -LiteralPath $resolvedData) {
        $rollback = "$resolvedData.before-restore-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Move-Item -LiteralPath $resolvedData -Destination $rollback
        Write-Output "Previous data retained at $rollback"
    }
    Move-Item -LiteralPath $source -Destination $resolvedData
    Write-Output "Restored data to $resolvedData"
} finally {
    if (Test-Path -LiteralPath $temporary) {
        Remove-Item -LiteralPath $temporary -Recurse -Force
    }
}

