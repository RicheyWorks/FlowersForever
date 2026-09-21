// Exercise the production PowerShell doctor validator with JSON reports only.
// Extracting its AST avoids starting any real packaged executable or database.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, realpath, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const runner = String.raw`
param([string]$SourcePath, [string]$CasesPath, [string]$DataDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($SourcePath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) { throw 'Launcher helper has PowerShell parse errors.' }
$function = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-McpDoctorReady'
}, $true)
if ($null -eq $function) { throw 'Launcher helper has no doctor validator.' }
$calls = $ast.FindAll({ param($node)
    $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Assert-McpDoctorReady'
}, $true)
if ($calls.Count -ne 1) { throw 'Launcher helper must call its doctor validator.' }
. ([scriptblock]::Create($function.Extent.Text))
$results = foreach ($case in (Get-Content -LiteralPath $CasesPath -Raw | ConvertFrom-Json)) {
    try {
        Assert-McpDoctorReady $case.report $DataDirectory
        [ordered]@{name = $case.name; accepted = $true}
    } catch {
        [ordered]@{name = $case.name; accepted = $false}
    }
}
ConvertTo-Json -InputObject @($results) -Compress
`;

test('Windows doctor readiness accepts current and future catalogs while rejecting incomplete operations', async t => {
  const temp = await realpath(os.tmpdir());
  const root = await mkdtemp(path.join(temp, 'flowersforever-windows-doctor-'));
  t.after(async () => {
    const resolved = await realpath(root);
    assert.ok(resolved.startsWith(temp + path.sep + 'flowersforever-windows-doctor-'));
    await rm(resolved, { recursive: true, force: true });
  });
  const ready = {
    ready: true,
    dataDirectory: root,
    enabledFarmDataToolCount: 38,
    missingFarmDataTools: [],
    capabilities: { connectAndMap: true, readBackAndResume: true, migrateAndOperate: true }
  };
  const cases = [];
  const add = (name, accepted, change = () => {}) => {
    const report = structuredClone(ready);
    change(report);
    cases.push({ name, accepted, report });
  };
  add('current 38 operations', true);
  add('additional operations', true, report => { report.enabledFarmDataToolCount = 39; });
  add('missing required handoff with unchanged count', false,
    report => { report.missingFarmDataTools = ['farm-data__complete-order-handoff']; });
  add('missing operation list', false, report => { delete report.missingFarmDataTools; });
  add('disabled capability', false, report => { report.capabilities.migrateAndOperate = false; });
  add('missing capability', false, report => { delete report.capabilities.readBackAndResume; });
  add('invalid count', false, report => { report.enabledFarmDataToolCount = 0; });
  add('fractional count', false, report => { report.enabledFarmDataToolCount = 38.5; });
  add('not ready', false, report => { report.ready = false; });
  add('nonboolean readiness', false, report => { report.ready = 'true'; });
  add('wrong data directory', false, report => { report.dataDirectory = path.join(root, 'wrong'); });
  const runnerPath = path.join(root, 'validate-fixtures.ps1');
  const casesPath = path.join(root, 'cases.json');
  await Promise.all([writeFile(runnerPath, runner), writeFile(casesPath, JSON.stringify(cases))]);
  const result = await new Promise((resolve, reject) => {
    const child = spawn(process.env.PWSH_EXECUTABLE || 'pwsh', ['-NoLogo', '-NoProfile', '-NonInteractive',
      '-File', runnerPath, '-SourcePath', fileURLToPath(new URL('./test-mcp-launcher.ps1', import.meta.url)),
      '-CasesPath', casesPath, '-DataDirectory', root],
    { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', chunk => { stdout += chunk; });
    child.stderr.on('data', chunk => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', code => resolve({ code, stdout, stderr }));
  });
  assert.equal(result.code, 0, result.stderr);
  assert.deepEqual(JSON.parse(result.stdout), cases.map(({ name, accepted }) => ({ name, accepted })));
});
