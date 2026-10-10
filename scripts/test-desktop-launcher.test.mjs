// Exercise the production wait/marker/cleanup gate with disposable PowerShell
// children. No FlowersForever executable, database, or GUI is started.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, realpath, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const child = String.raw`
param([string]$Scenario)
$ErrorActionPreference = 'Stop'
$data = $env:FLOWERFARM_DATA_DIR
Set-Content -LiteralPath (Join-Path $data 'child.pid') -Value $PID
$marker = Join-Path $data 'release-smoke.ok'
switch ($Scenario) {
    'missing' { exit 0 }
    'invalid' { Set-Content -LiteralPath $marker -Value 'Not a smoke success'; exit 0 }
    'empty' { Set-Content -LiteralPath $marker -Value ''; exit 0 }
    'timeout-without-marker' { Start-Sleep -Seconds 60; exit 0 }
}
Set-Content -LiteralPath $marker -Value 'Packaged runtime started successfully at 2026-09-22T12:00:00Z'
if ($Scenario -eq 'failed-shutdown') { exit 7 }
if ($Scenario -eq 'timeout-after-marker') { Start-Sleep -Seconds 60 }
if ($Scenario -eq 'delayed-shutdown') { Start-Sleep -Milliseconds 750 }
exit 0
`;

const runner = String.raw`
param([string]$SourcePath, [string]$Root)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($SourcePath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) { throw 'Desktop helper has PowerShell parse errors.' }
$function = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-DesktopSmoke'
}, $true)
if ($null -eq $function) { throw 'Desktop helper has no process gate.' }
$calls = $ast.FindAll({ param($node)
    $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Invoke-DesktopSmoke'
}, $true)
if ($calls.Count -ne 1) { throw 'Desktop helper must call its process gate.' }
. ([scriptblock]::Create($function.Extent.Text))
$results = foreach ($scenario in @('success', 'delayed-shutdown', 'failed-shutdown', 'missing',
        'invalid', 'empty', 'timeout-after-marker', 'timeout-without-marker', 'stale-file', 'stale-directory')) {
    $data = Join-Path $Root $scenario
    New-Item -ItemType Directory -Path $data | Out-Null
    $marker = Join-Path $data 'release-smoke.ok'
    if ($scenario -eq 'stale-file') { Set-Content -LiteralPath $marker -Value 'previous evidence' }
    if ($scenario -eq 'stale-directory') { New-Item -ItemType Directory -Path $marker | Out-Null }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Process -Id $PID).Path
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-File',
            (Join-Path $Root 'child.ps1'), $scenario)) {
        $start.ArgumentList.Add($argument)
    }
    $start.Environment['FLOWERFARM_DATA_DIR'] = $data
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $failure = ''
    $timeout = if ($scenario.StartsWith('timeout-')) { 2 } else { 10 }
    try { Invoke-DesktopSmoke $start $data $timeout | Out-Null } catch { $failure = $_.Exception.Message }
    $elapsed = $timer.ElapsedMilliseconds
    $pidFile = Join-Path $data 'child.pid'
    $launched = Test-Path -LiteralPath $pidFile
    $running = $false
    if ($launched) {
        $childId = [int](Get-Content -LiteralPath $pidFile -Raw)
        $running = $null -ne (Get-Process -Id $childId -ErrorAction SilentlyContinue)
    }
    [ordered]@{scenario = $scenario; failure = $failure; launched = $launched;
        running = $running; elapsed = $elapsed;
        stalePreserved = ($scenario -ne 'stale-file' -or
            (Get-Content -LiteralPath $marker -Raw).Trim() -eq 'previous evidence')}
}
ConvertTo-Json -InputObject @($results) -Compress
`;

test('desktop smoke requires fresh evidence, clean exit, and bounded shutdown', async t => {
  const temp = await realpath(os.tmpdir());
  const root = await mkdtemp(path.join(temp, 'flowersforever-desktop-smoke-'));
  t.after(async () => {
    const resolved = await realpath(root);
    assert.ok(resolved.startsWith(temp + path.sep + 'flowersforever-desktop-smoke-'));
    await rm(resolved, { recursive: true, force: true });
  });
  const runnerPath = path.join(root, 'runner.ps1');
  await Promise.all([writeFile(runnerPath, runner), writeFile(path.join(root, 'child.ps1'), child)]);
  const result = await new Promise((resolve, reject) => {
    const process = spawn(globalThis.process.env.PWSH_EXECUTABLE || 'pwsh',
      ['-NoLogo', '-NoProfile', '-NonInteractive', '-File', runnerPath, '-SourcePath',
        fileURLToPath(new URL('./test-desktop-launcher.ps1', import.meta.url)), '-Root', root],
      { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';
    process.stdout.on('data', chunk => { stdout += chunk; });
    process.stderr.on('data', chunk => { stderr += chunk; });
    process.on('error', reject);
    process.on('close', code => resolve({ code, stdout, stderr }));
  });
  assert.equal(result.code, 0, result.stderr);
  const expected = {
    success: '',
    'delayed-shutdown': '',
    'failed-shutdown': 'Desktop launcher exited with code 7.',
    missing: 'Desktop launcher did not create release-smoke.ok.',
    invalid: 'Desktop smoke marker has unexpected contents.',
    empty: 'Desktop smoke marker has unexpected contents.',
    'timeout-after-marker': 'Desktop launcher did not exit before the timeout.',
    'timeout-without-marker': 'Desktop launcher did not exit before the timeout.',
    'stale-file': 'A desktop smoke marker already exists; it was preserved.',
    'stale-directory': 'A desktop smoke marker already exists; it was preserved.'
  };
  const results = JSON.parse(result.stdout);
  assert.equal(results.length, Object.keys(expected).length);
  for (const item of results) {
    await t.test(item.scenario, () => {
      assert.equal(item.failure, expected[item.scenario]);
      assert.equal(item.running, false, 'the child must be stopped before the gate returns');
      assert.equal(item.launched, !item.scenario.startsWith('stale-'));
      assert.equal(item.stalePreserved, true);
      if (item.scenario === 'delayed-shutdown') assert.ok(item.elapsed >= 750);
      if (item.scenario.startsWith('timeout-')) assert.ok(item.elapsed < 10000);
    });
  }
});
