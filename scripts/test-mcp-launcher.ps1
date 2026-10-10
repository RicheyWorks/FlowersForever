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

$resolvedLauncher = [IO.Path]::GetFullPath($Launcher)
if (-not (Test-Path -LiteralPath $resolvedLauncher -PathType Leaf)) {
    throw "MCP launcher does not exist: $resolvedLauncher"
}
$resolvedData = [IO.Path]::GetFullPath($DataDir)
New-Item -ItemType Directory -Path $resolvedData -Force | Out-Null
$token = [Convert]::ToBase64String(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(24))

function New-McpProcess([string[]]$Arguments) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $resolvedLauncher
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) {
        $start.ArgumentList.Add($argument)
    }
    $start.Environment['FLOWERFARM_DATA_DIR'] = $resolvedData
    $start.Environment['FLOWERFARM_HARNESS_TOKEN'] = $token
    $start.Environment['FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ'] = 'true'
    $start.Environment['FLOWERFARM_HARNESS_ALLOW_MUTATIONS'] = 'true'
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) {
        throw 'Could not start the MCP launcher.'
    }
    return $process
}

function Stop-McpProcess([Diagnostics.Process]$Process) {
    if (-not $Process.HasExited) {
        $Process.Kill($true)
        $Process.WaitForExit()
    }
    $Process.Dispose()
}

function Assert-McpDoctorReady([object]$Doctor, [string]$ExpectedDataDirectory) {
    if ($Doctor.ready -isnot [bool] -or -not $Doctor.ready) {
        throw 'MCP doctor did not report ready=true.'
    }
    # The application owns the required operation sets. Check that contract rather
    # than pinning this smoke helper to a count that changes when tools are added.
    if ($Doctor.missingFarmDataTools -isnot [array] -or $Doctor.missingFarmDataTools.Count -ne 0) {
        throw 'MCP doctor reported missing required farm-data operations.'
    }
    foreach ($capability in @('connectAndMap', 'readBackAndResume', 'migrateAndOperate')) {
        if ($Doctor.capabilities.$capability -isnot [bool] -or -not $Doctor.capabilities.$capability) {
            throw "MCP doctor did not enable the required $capability capability."
        }
    }
    if (($Doctor.enabledFarmDataToolCount -isnot [long] -and $Doctor.enabledFarmDataToolCount -isnot [int]) `
            -or $Doctor.enabledFarmDataToolCount -le 0) {
        throw 'MCP doctor did not report a positive farm-data operation count.'
    }
    if ($Doctor.dataDirectory -isnot [string] `
            -or [string]::IsNullOrWhiteSpace($Doctor.dataDirectory) `
            -or [IO.Path]::GetFullPath($Doctor.dataDirectory) -ne $ExpectedDataDirectory) {
        throw 'MCP doctor did not use the requested shared data directory.'
    }
}

$doctorProcess = New-McpProcess @('--mcp-doctor')
$doctorProcess.StandardInput.Close()
$doctorOut = $doctorProcess.StandardOutput.ReadToEndAsync()
$doctorErr = $doctorProcess.StandardError.ReadToEndAsync()
try {
    if (-not $doctorProcess.WaitForExit($TimeoutSeconds * 1000)) {
        throw 'MCP doctor did not exit before the timeout.'
    }
    if ($doctorProcess.ExitCode -ne 0) {
        throw "MCP doctor exited with code $($doctorProcess.ExitCode): $($doctorErr.Result)"
    }
    $doctor = $doctorOut.Result | ConvertFrom-Json
    Assert-McpDoctorReady $doctor $resolvedData
    if ($doctorOut.Result.Contains($token) -or $doctorErr.Result.Contains($token)) {
        throw 'MCP doctor exposed its process token.'
    }
} finally {
    Stop-McpProcess $doctorProcess
}

$serverProcess = New-McpProcess @()
$serverErr = $serverProcess.StandardError.ReadToEndAsync()
try {
    $ping = '{"jsonrpc":"2.0","id":"release-smoke","method":"ping",' +
        '"params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",' +
        '"io.modelcontextprotocol/clientCapabilities":{}}}}'
    $serverProcess.StandardInput.WriteLine($ping)
    $serverProcess.StandardInput.Flush()
    $responseTask = $serverProcess.StandardOutput.ReadLineAsync()
    if (-not $responseTask.Wait($TimeoutSeconds * 1000)) {
        throw 'MCP launcher did not answer ping before the timeout.'
    }
    $response = $responseTask.Result | ConvertFrom-Json
    if ($response.id -ne 'release-smoke' -or $null -eq $response.result) {
        throw 'MCP launcher returned an invalid ping response.'
    }
    $serverProcess.StandardInput.Close()
    if (-not $serverProcess.WaitForExit($TimeoutSeconds * 1000)) {
        throw 'MCP launcher did not exit after stdin closed.'
    }
    if ($serverProcess.ExitCode -ne 0) {
        throw "MCP launcher exited with code $($serverProcess.ExitCode)."
    }
    if (-not $serverErr.Result.Contains('FlowersForever MCP stdio adapter ready')) {
        throw 'MCP launcher did not publish its ready diagnostic on stderr.'
    }
    if ($serverErr.Result.Contains($token)) {
        throw 'MCP launcher exposed its process token.'
    }
    $remainder = $serverProcess.StandardOutput.ReadToEnd()
    if (-not [string]::IsNullOrWhiteSpace($remainder)) {
        throw "MCP stdout contained non-protocol output: $remainder"
    }
} finally {
    Stop-McpProcess $serverProcess
}

[ordered]@{
    ready = $true
    launcher = $resolvedLauncher
    dataDirectory = $resolvedData
    schemaVersion = $doctor.schemaVersion
    enabledFarmDataToolCount = $doctor.enabledFarmDataToolCount
    stdioPing = $true
} | ConvertTo-Json -Compress
