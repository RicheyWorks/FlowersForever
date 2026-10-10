#Requires -Version 7.0
param(
    [Parameter(Mandatory = $true)]
    [string]$Launcher,
    [Parameter(Mandatory = $true)]
    [string]$DataDir,
    [ValidateRange(1, 600)]
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-DesktopSmoke(
    [Diagnostics.ProcessStartInfo]$StartInfo,
    [string]$DataDirectory,
    [int]$TimeoutSeconds
) {
    New-Item -ItemType Directory -Path $DataDirectory -Force | Out-Null
    $marker = Join-Path $DataDirectory 'release-smoke.ok'
    if ($null -ne (Get-Item -LiteralPath $marker -Force -ErrorAction SilentlyContinue)) {
        throw 'A desktop smoke marker already exists; it was preserved.'
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $StartInfo
    $started = $false
    try {
        $started = $process.Start()
        if (-not $started) { throw 'Could not start the desktop launcher.' }
        # The marker is written before Spring closes. It cannot prove shutdown
        # succeeded or that the runtime released its database locks.
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            throw 'Desktop launcher did not exit before the timeout.'
        }
        if ($process.ExitCode -ne 0) {
            throw "Desktop launcher exited with code $($process.ExitCode)."
        }
        if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) {
            throw 'Desktop launcher did not create release-smoke.ok.'
        }
        $contents = Get-Content -LiteralPath $marker -Raw
        if ([string]::IsNullOrWhiteSpace($contents) -or
                -not $contents.StartsWith('Packaged runtime started successfully at ')) {
            throw 'Desktop smoke marker has unexpected contents.'
        }
        Get-Content -LiteralPath $marker
    } finally {
        if ($started -and -not $process.HasExited) {
            $process.Kill($true)
            if (-not $process.WaitForExit(5000)) {
                throw 'Desktop launcher did not stop after timeout cleanup.'
            }
        }
        $process.Dispose()
    }
}

$resolvedLauncher = [IO.Path]::GetFullPath($Launcher)
if (-not (Test-Path -LiteralPath $resolvedLauncher -PathType Leaf)) {
    throw "Desktop launcher does not exist: $resolvedLauncher"
}
$resolvedData = [IO.Path]::GetFullPath($DataDir)
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = $resolvedLauncher
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
$start.ArgumentList.Add('--release-smoke')
$start.Environment['FLOWERFARM_DATA_DIR'] = $resolvedData
Invoke-DesktopSmoke $start $resolvedData $TimeoutSeconds
