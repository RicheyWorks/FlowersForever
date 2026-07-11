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
Get-Json "/api/briefing"
Get-Json "/api/closeout"
Get-Json "/api/harvest/week"
Get-Json "/api/orders/week"
Get-Json "/api/connectors"
Get-Json "/api/connectors/history?limit=10"
Get-Json "/api/inventory"
Get-Json "/api/inventory/low-stock?threshold=10"
Get-Json "/api/irrigation/advice?live=false"
Get-Json "/api/market-day"
Get-Json "/api/orders"
Get-Json "/api/harvest/beds?week=true"

Get-Json "/api/harvest/log?week=true"

$logPdf = Join-Path (Get-Location) "harvest-log-demo.pdf"
Write-Host "`n=== GET /api/harvest/log/report.pdf?week=true → $logPdf ===" -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/harvest/log/report.pdf?week=true" -Headers $headers -OutFile $logPdf
    Write-Host "Wrote $logPdf ($((Get-Item $logPdf).Length) bytes)" -ForegroundColor Green
} catch {
    Write-Host "Harvest log PDF failed: $_" -ForegroundColor Red
}

$bedPdf = Join-Path (Get-Location) "bed-production-demo.pdf"
Write-Host "`n=== GET /api/harvest/beds/report.pdf?week=true → $bedPdf ===" -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/harvest/beds/report.pdf?week=true" -Headers $headers -OutFile $bedPdf
    Write-Host "Wrote $bedPdf ($((Get-Item $bedPdf).Length) bytes)" -ForegroundColor Green
} catch {
    Write-Host "Bed production PDF failed: $_" -ForegroundColor Red
}

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

$briefPdf = Join-Path (Get-Location) "morning-briefing-demo.pdf"
Write-Host "`n=== GET /api/briefing/report.pdf → $briefPdf ===" -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/briefing/report.pdf" -Headers $headers -OutFile $briefPdf
    Write-Host "Wrote $briefPdf ($((Get-Item $briefPdf).Length) bytes)" -ForegroundColor Green
} catch {
    Write-Host "Briefing PDF failed: $_" -ForegroundColor Red
}

$closePdf = Join-Path (Get-Location) "day-closeout-demo.pdf"
Write-Host "`n=== GET /api/closeout/report.pdf → $closePdf ===" -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/closeout/report.pdf" -Headers $headers -OutFile $closePdf
    Write-Host "Wrote $closePdf ($((Get-Item $closePdf).Length) bytes)" -ForegroundColor Green
} catch {
    Write-Host "Closeout PDF failed: $_" -ForegroundColor Red
}

$lowPdf = Join-Path (Get-Location) "low-stock-reorder-demo.pdf"
Write-Host "`n=== GET /api/inventory/low-stock/report.pdf → $lowPdf ===" -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/inventory/low-stock/report.pdf?threshold=10" -Headers $headers -OutFile $lowPdf
    Write-Host "Wrote $lowPdf ($((Get-Item $lowPdf).Length) bytes)" -ForegroundColor Green
} catch {
    Write-Host "Low-stock PDF failed: $_" -ForegroundColor Red
}

# First order / customer id when present (demo profile seeds CONFIRMED orders)
try {
    $orders = Invoke-RestMethod -Uri "$BaseUrl/api/orders" -Headers $headers
    if ($orders -and $orders.Count -gt 0) {
        $oid = $orders[0].id
        $invPdf = Join-Path (Get-Location) "invoice-order-$oid-demo.pdf"
        Write-Host "`n=== GET /api/orders/$oid/invoice.pdf → $invPdf ===" -ForegroundColor Yellow
        Invoke-WebRequest -Uri "$BaseUrl/api/orders/$oid/invoice.pdf" -Headers $headers -OutFile $invPdf
        Write-Host "Wrote $invPdf ($((Get-Item $invPdf).Length) bytes)" -ForegroundColor Green
    } else {
        Write-Host "`n(no orders — skip invoice PDF; use --spring.profiles.active=demo)" -ForegroundColor DarkYellow
    }
} catch {
    Write-Host "Invoice PDF failed: $_" -ForegroundColor Red
}

try {
    $customers = Invoke-RestMethod -Uri "$BaseUrl/api/customers" -Headers $headers
    if ($customers -and $customers.Count -gt 0) {
        $cid = $customers[0].id
        $stPdf = Join-Path (Get-Location) "statement-customer-$cid-demo.pdf"
        Write-Host "`n=== GET /api/customers/$cid/statement.pdf → $stPdf ===" -ForegroundColor Yellow
        Invoke-WebRequest -Uri "$BaseUrl/api/customers/$cid/statement.pdf" -Headers $headers -OutFile $stPdf
        Write-Host "Wrote $stPdf ($((Get-Item $stPdf).Length) bytes)" -ForegroundColor Green
    } else {
        Write-Host "`n(no customers — skip statement PDF; use --spring.profiles.active=demo)" -ForegroundColor DarkYellow
    }
} catch {
    Write-Host "Statement PDF failed: $_" -ForegroundColor Red
}

Write-Host "`nDone." -ForegroundColor Green
