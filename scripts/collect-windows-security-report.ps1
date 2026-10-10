#Requires -Version 5.1
<#
.SYNOPSIS
Collects local installer/launcher metadata for a Windows security review.
.DESCRIPTION
Reads only the two named launchers and optional MSI. Does not run them, read
farm data, upload files, change protection settings, or restore quarantine.
BuildCommit is an optional maintainer assertion, not inferred build provenance.
#>
[CmdletBinding()]
param(
    [string]$AppImage,
    [ValidatePattern('\.msi$')]
    [string]$Installer,
    [ValidatePattern('^[0-9a-fA-F]{7,40}$')]
    [string]$BuildCommit,
    [string]$DetectionName,
    [string]$AlertId,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'Run this report on Windows to inspect Authenticode signatures.'
}
if ([string]::IsNullOrWhiteSpace($AppImage) -and [string]::IsNullOrWhiteSpace($Installer)) {
    throw 'Supply -AppImage (the folder containing the launchers), -Installer, or both.'
}

function Get-ArtifactEvidence([string]$Path) {
    $resolved = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
    $evidence = [ordered]@{
        fileName = [IO.Path]::GetFileName($resolved)
        state = 'missing'
        sizeBytes = $null
        sha256 = $null
        fileVersion = $null
        signatureStatus = $null
        signerSubject = $null
        signerThumbprint = $null
        timestampAuthority = $null
    }
    try {
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            return [pscustomobject]$evidence
        }
        $file = Get-Item -LiteralPath $resolved
        $evidence.sizeBytes = $file.Length
        $evidence.sha256 = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
        $evidence.fileVersion = $file.VersionInfo.FileVersion
        $signature = Get-AuthenticodeSignature -LiteralPath $resolved
        $evidence.signatureStatus = [string]$signature.Status
        if ($null -ne $signature.SignerCertificate) {
            $evidence.signerSubject = $signature.SignerCertificate.Subject
            $evidence.signerThumbprint = $signature.SignerCertificate.Thumbprint
        }
        if ($null -ne $signature.TimeStamperCertificate) {
            $evidence.timestampAuthority = $signature.TimeStamperCertificate.Subject
        }
        $evidence.state = 'collected'
    } catch {
        # Keep file paths and arbitrary exception contents out of the shared report.
        $evidence.state = 'unreadable'
    }
    return [pscustomobject]$evidence
}

$artifacts = @()
if (-not [string]::IsNullOrWhiteSpace($AppImage)) {
    foreach ($name in @('FlowersForever.exe', 'FlowersForeverAI.exe')) {
        $artifacts += Get-ArtifactEvidence (Join-Path $AppImage $name)
    }
}
if (-not [string]::IsNullOrWhiteSpace($Installer)) {
    $artifacts += Get-ArtifactEvidence $Installer
}
$report = [ordered]@{
    schemaVersion = 1
    collectedAtUtc = [DateTime]::UtcNow.ToString('o')
    product = 'FlowersForever'
    reportedBuildCommit = $BuildCommit
    buildCommitSource = 'Caller supplied; match artifact hashes to the build records separately.'
    detectionName = $DetectionName
    alertId = $AlertId
    artifacts = $artifacts
    interpretation = @(
        'File metadata is not a malware verdict or permission to run a blocked file.',
        'A missing file may have been quarantined or removed; this report does not restore it.',
        'A valid signature identifies a publisher and protects integrity; it does not guarantee antivirus clearance.'
    )
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $project = Split-Path -Parent $PSScriptRoot
    $OutputPath = Join-Path $project ('target\windows-security-report-' +
        [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N') + '.json')
}
$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Path $parent -Force | Out-Null
# CreateNew prevents accidental replacement of an artifact or earlier report.
$stream = [IO.File]::Open($resolvedOutput, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
$writer = $null
try {
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    $writer.WriteLine(($report | ConvertTo-Json -Depth 6))
} finally {
    if ($null -ne $writer) { $writer.Dispose() } else { $stream.Dispose() }
}
Write-Output $resolvedOutput
