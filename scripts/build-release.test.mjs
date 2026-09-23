// Run the local packaging orchestrator in a disposable project. Maven,
// jpackage, and smoke launches are fixtures; no native application is built.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { copyFile, mkdir, mkdtemp, readFile, readdir, realpath, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

const runner = String.raw`
param([string]$Root, [switch]$FailDesktop)
$ErrorActionPreference = 'Stop'
$env:RELEASE_FIXTURE_ROOT = $Root
$env:RELEASE_FIXTURE_FAIL_DESKTOP = [string][bool]$FailDesktop
function Add-Event([string]$Name, [object]$Values) {
    ConvertTo-Json -InputObject @{name = $Name; values = $Values} -Compress -Depth 5 |
        Add-Content -LiteralPath (Join-Path $Root 'events.jsonl')
}
function mvn {
    Add-Event 'maven' @($args)
    $property = @($args | Where-Object { $_ -like '-Dflowerfarm.build.directory=*' })
    if ($property.Count -ne 1) { throw 'Maven output must be isolated.' }
    $output = $property[0].Substring('-Dflowerfarm.build.directory='.Length)
    New-Item -ItemType Directory -Path (Join-Path $output 'generated-resources/licenses') -Force | Out-Null
    foreach ($file in @('flowerfarm-manager-1.2.0.jar', 'bom.json', 'bom.xml',
            'generated-resources/licenses/THIRD-PARTY.txt')) {
        Set-Content -LiteralPath (Join-Path $output $file) -Value "fixture $file"
    }
    $global:LASTEXITCODE = 0
}
function jpackage {
    Add-Event 'jpackage' @($args)
    $output = [string]$args[[Array]::IndexOf($args, '--dest') + 1]
    $type = [string]$args[[Array]::IndexOf($args, '--type') + 1]
    if ($type -eq 'app-image') {
        $image = Join-Path $output 'FlowersForever'
        New-Item -ItemType Directory -Path $image | Out-Null
        foreach ($file in @('FlowersForever.exe', 'FlowersForeverAI.exe')) {
            Set-Content -LiteralPath (Join-Path $image $file) -Value 'fixture launcher bytes'
        }
    } elseif ($type -eq 'msi') {
        Set-Content -LiteralPath (Join-Path $output 'FlowersForever-1.2.0.msi') -Value 'fixture MSI'
    } else { throw "Unexpected packaging type: $type" }
    $global:LASTEXITCODE = 0
}
& (Join-Path $Root 'scripts/build-release.ps1')
if (-not $FailDesktop) { & (Join-Path $Root 'scripts/build-release.ps1') -SkipTests -SkipMsi }
`;

const smokeFixture = String.raw`
param([string]$Launcher, [string]$DataDir)
$ErrorActionPreference = 'Stop'
$kind = if ($MyInvocation.MyCommand.Name -eq 'test-mcp-launcher.ps1') { 'mcp' } else { 'desktop' }
if (-not (Test-Path -LiteralPath $Launcher -PathType Leaf)) { throw 'Missing fixture launcher.' }
ConvertTo-Json -InputObject @{name = $kind; values = @{launcher = $Launcher; data = $DataDir}} -Compress |
    Add-Content -LiteralPath (Join-Path $env:RELEASE_FIXTURE_ROOT 'events.jsonl')
if ($kind -eq 'desktop' -and $env:RELEASE_FIXTURE_FAIL_DESKTOP -eq 'True') { throw 'Fixture desktop shutdown failed.' }
`;

async function runFixture(t, failDesktop) {
  const temp = await realpath(os.tmpdir());
  const root = await mkdtemp(path.join(temp, 'flowersforever-packaging-'));
  t.after(async () => {
    const resolved = await realpath(root);
    assert.ok(resolved.startsWith(temp + path.sep + 'flowersforever-packaging-'));
    await rm(resolved, { recursive: true, force: true });
  });
  const preserved = ['dist/harness-evidence/latest.json', 'dist/FlowersForever/FlowersForever.exe',
    'dist/old.msi', 'target/FlowersForever/FlowersForever.exe', 'target/jpackage-input/preserve.txt'];
  for (const relative of [...preserved, 'packaging/FlowersForeverAI-windows.properties']) {
    const file = path.join(root, relative);
    await mkdir(path.dirname(file), { recursive: true });
    await writeFile(file, 'preserve ' + relative);
  }
  await mkdir(path.join(root, 'scripts'));
  await Promise.all([
    copyFile(new URL('./build-release.ps1', import.meta.url), path.join(root, 'scripts/build-release.ps1')),
    writeFile(path.join(root, 'scripts/test-mcp-launcher.ps1'), smokeFixture),
    writeFile(path.join(root, 'scripts/test-desktop-launcher.ps1'), smokeFixture),
    writeFile(path.join(root, 'pom.xml'), '<project><version>1.2.0</version></project>'),
    writeFile(path.join(root, 'runner.ps1'), runner)
  ]);
  const result = await new Promise((resolve, reject) => {
    const child = spawn(process.env.PWSH_EXECUTABLE || 'pwsh', ['-NoLogo', '-NoProfile', '-NonInteractive',
      '-File', path.join(root, 'runner.ps1'), '-Root', root, ...(failDesktop ? ['-FailDesktop'] : [])],
    { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', chunk => { stdout += chunk; });
    child.stderr.on('data', chunk => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', code => resolve({ code, stdout, stderr }));
  });
  for (const relative of preserved) {
    assert.equal(await readFile(path.join(root, relative), 'utf8'), 'preserve ' + relative);
  }
  const events = (await readFile(path.join(root, 'events.jsonl'), 'utf8')).trim().split(/\r?\n/).map(JSON.parse);
  return { root, result, events };
}

test('local release outputs preserve existing evidence and binaries across repeated builds', async t => {
  const { root, result, events } = await runFixture(t, false);
  assert.equal(result.code, 0, result.stderr);
  assert.deepEqual(events.map(event => event.name),
    ['maven', 'jpackage', 'mcp', 'desktop', 'jpackage', 'maven', 'jpackage', 'mcp', 'desktop']);
  const releases = (await readdir(path.join(root, 'dist'))).filter(name => name.startsWith('release-'));
  assert.equal(releases.length, 2);
  for (const release of releases) {
    const output = path.join(root, 'dist', release);
    const files = await readdir(output);
    assert.ok(files.includes('FlowersForever-1.2.0-windows-app-image.zip'));
    const manifest = await readFile(path.join(output, 'SHA256SUMS.txt'), 'utf8');
    assert.ok(manifest.includes('flowerfarm-manager-1.2.0.jar'));
    assert.ok(!manifest.includes('old.msi'));
    assert.ok(!manifest.includes('latest.json'));
    assert.ok((await readdir(path.join(root, 'target'))).includes(release));
  }
  assert.ok(events[0].values.includes('verify'));
  assert.ok(events[5].values.includes('-DskipTests'));
  for (const index of [2, 7]) {
    assert.equal(events[index].values.data, events[index + 1].values.data,
      'MCP and desktop smoke must reuse the same disposable database');
  }
  const image = events[3].values.launcher;
  const msiArgs = events[4].values;
  assert.equal(path.dirname(image), msiArgs[msiArgs.indexOf('--app-image') + 1]);
});

test('desktop shutdown failure prevents ZIP, installer, and completion manifest', async t => {
  const { root, result, events } = await runFixture(t, true);
  assert.notEqual(result.code, 0);
  assert.match(result.stderr, /Fixture desktop shutdown failed/);
  assert.deepEqual(events.map(event => event.name), ['maven', 'jpackage', 'mcp', 'desktop']);
  const releases = (await readdir(path.join(root, 'dist'))).filter(name => name.startsWith('release-'));
  assert.equal(releases.length, 1);
  assert.deepEqual(await readdir(path.join(root, 'dist', releases[0])), ['FlowersForever']);
});
