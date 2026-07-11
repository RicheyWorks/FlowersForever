# FlowersForever — REST smoke script for demos (app must be running on :8080)
# Usage:  powershell -File scripts/demo-rest.ps1
# Auth:   powershell -File scripts/demo-rest.ps1 -User farm -Pass kitsap

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "",
    [string]$Pass = ""
)

$ErrorActionPreference = "Stop"
$headers = @{}
if ($User -ne "" -and $Pass -ne "") {
    $token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${User}:${Pass}"))
    $headers["Authorization"] = "Basic $token"
    Write-Host "Using Basic auth as $User" -ForegroundColor Cyan
}

function Get-Json($path) {
    $uri = "$BaseUrl$path"
    Write-Host "`n=== GET $path ===" -ForegroundColor Yellow
    try {
        $r = Invoke-RestMethod -Uri $uri -Headers $headers
        $r | ConvertTo-Json -Depth 6
    } catch {
        Write-Host "FAILED: $_" -ForegroundColor Red
    }
}

Write-Host "FlowersForever demo REST smoke → $BaseUrl" -ForegroundColor Green

Get-Json "/actuator/health"
Get-Json "/api/auth/me"
Get-Json "/api/auth/accounts"
Get-Json "/api/dashboard"
Get-Json "/api/harvest/week"
Get-Json "/api/orders/week"
Get-Json "/api/connectors"
Get-Json "/api/connectors/history?limit=10"
Get-Json "/api/inventory"
Get-Json "/api/irrigation/advice?live=false"
Get-Json "/api/market-day"
Get-Json "/api/harvest/beds?week=true"

$packPdf = Join-Path (Get-Location) "market-pack-demo.pdf"
Write-Host "`n=== GET /api/market-day/packing.pdf → $packPdf ===" -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/market-day/packing.pdf" -Headers $headers -OutFile $packPdf
    Write-Host "Wrote $packPdf ($((Get-Item $packPdf).Length) bytes)" -ForegroundColor Green
} catch {
    Write-Host "Packing PDF failed: $_" -ForegroundColor Red
}

$pdf = Join-Path (Get-Location) "weekly-demo.pdf"
Write-Host "`n=== GET /api/reports/weekly.pdf → $pdf ===" -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/reports/weekly.pdf" -Headers $headers -OutFile $pdf
    Write-Host "Wrote $pdf ($((Get-Item $pdf).Length) bytes)" -ForegroundColor Green
} catch {
    Write-Host "PDF failed: $_" -ForegroundColor Red
}

Write-Host "`nDone." -ForegroundColor Green
