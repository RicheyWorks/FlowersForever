// Behavioral smoke-helper tests: fake executable launchers and jcmd, no Java/DB.
// Uses system Bash and Python3; Windows uses Git Bash and Python (PYTHON override supported).
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { chmod, copyFile, mkdir, mkdtemp, readFile, readdir, realpath, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

const bash = process.env.BASH_EXECUTABLE || (process.platform === 'win32'
  ? 'C:/Program Files/Git/bin/bash.exe' : 'bash');
const token = 'fixture-macos-harness-token-123456789';
const fakeLauncher = `#!/usr/bin/env bash
set -eu
if [[ "$0" == *AI ]]; then
  if [[ "\${1:-}" == '--mcp-doctor' ]]; then phase=doctor; else phase=ping; fi
else phase=desktop; fi
printf '%s|%s|%s\\n' "$phase" "$FLOWERFARM_DATA_DIR" "$FLOWERFARM_HARNESS_ALLOW_MUTATIONS" >> "$FLOWERFARM_DATA_DIR/phase-paths"
printf '%s\\n' "$$" > "$FLOWERFARM_DATA_DIR/$phase.pid"
case "$SCENARIO:$phase" in
  exit-failure:doctor) echo 'fixture failure: Server is running; SQL statement: 127.0.0.1:1234/0123456789abcdef0123456789abcdef0123456789abc' >&2; exit 7 ;;
  timeout:doctor) while :; do sleep 0.1; done ;;
  token-leak:doctor) echo "$FLOWERFARM_HARNESS_TOKEN" >&2 ;;
esac
if [[ "$phase" == doctor ]]; then
  "$PYTHON" - <<'PY'
import json, os
directory = os.environ['FLOWERFARM_DATA_DIR']
if os.environ['SCENARIO'] == 'wrong-directory': directory += '/wrong'
print(json.dumps({'ready': True, 'dataDirectory': directory}))
PY
elif [[ "$phase" == ping ]]; then
  cat >/dev/null
  if [[ "$SCENARIO" == ping-error ]]; then
    echo '{"jsonrpc":"2.0","id":"release-smoke","result":{},"error":{"code":-1}}'
  elif [[ "$SCENARIO" == ping-null ]]; then
    echo '{"jsonrpc":"2.0","id":"release-smoke","result":null}'
  else echo '{"jsonrpc":"2.0","id":"release-smoke","result":{}}'; fi
else
  if [[ "$SCENARIO" != missing-marker ]]; then
    echo 'Packaged runtime started successfully at fixture-time' > "$FLOWERFARM_DATA_DIR/release-smoke.ok"
  fi
  if [[ "$SCENARIO" == marker-before-exit ]]; then while :; do sleep 0.1; done; fi
fi
`;

async function setup(t, scenario = 'success') {
  const temp = await realpath(os.tmpdir());
  const root = await mkdtemp(path.join(temp, 'flowersforever-mac-smoke-'));
  t.after(async () => {
    const resolved = await realpath(root);
    assert.ok(resolved.startsWith(temp + path.sep + 'flowersforever-mac-smoke-'));
    await rm(resolved, { recursive: true, force: true });
  });
  const scripts = path.join(root, 'scripts');
  const apps = path.join(root, 'app images');
  const data = path.join(root, 'shared data');
  const javaHome = path.join(root, 'jdk');
  await Promise.all([mkdir(scripts), mkdir(apps), mkdir(data), mkdir(path.join(javaHome, 'bin'), { recursive: true })]);
  const script = path.join(scripts, 'test-macos-launchers.sh');
  await copyFile(new URL('./test-macos-launchers.sh', import.meta.url), script);
  for (const name of ['FlowersForeverAI', 'FlowersForever']) {
    await writeFile(path.join(apps, name), fakeLauncher);
    await chmod(path.join(apps, name), 0o755);
  }
  const jcmd = path.join(javaHome, 'bin/jcmd');
  await writeFile(jcmd, '#!/usr/bin/env bash\necho fixture-thread-dump\n');
  await chmod(jcmd, 0o755);
  const env = { ...process.env, SCENARIO: scenario, JAVA_HOME: javaHome.replaceAll('\\', '/'),
    PYTHON: process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3'),
    FLOWERFARM_HARNESS_TOKEN: token, FLOWERFARM_HARNESS_ALLOW_MUTATIONS: 'false' };
  const run = (extra = []) => new Promise((resolve, reject) => {
    const started = Date.now();
    const child = spawn(bash, [script.replaceAll('\\', '/'),
      '--ai-launcher', path.join(apps, 'FlowersForeverAI').replaceAll('\\', '/'),
      '--launcher', path.join(apps, 'FlowersForever').replaceAll('\\', '/'),
      '--data-dir', data.replaceAll('\\', '/'), '--timeout-seconds', '5', '--diagnostic-after-seconds', '2', ...extra],
    { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    child.stdout.on('data', chunk => { output += chunk; });
    child.stderr.on('data', chunk => { output += chunk; });
    child.on('error', reject);
    child.on('close', (code, signal) => resolve({ code, signal, output, elapsed: Date.now() - started }));
  });
  return { root, data, javaHome, env, run };
}

test('three successful phases use the same absolute directory and preserve caller permission gates', async t => {
  const f = await setup(t);
  await writeFile(path.join(f.data, 'flowerfarm.lock.db'),
    'method=file\nserver=127.0.0.1:9999\nhostName=localhost\nid=NEVER_PRINT_LOCK_ID\n');
  const result = await f.run();
  assert.equal(result.code, 0, result.output);
  const phases = (await readFile(path.join(f.data, 'phase-paths'), 'utf8')).trim().split(/\r?\n/).map(line => line.split('|'));
  assert.deepEqual(phases.map(parts => parts[0]), ['doctor', 'ping', 'desktop']);
  assert.equal(new Set(phases.map(parts => parts[1])).size, 1);
  assert.ok(phases[0][1].startsWith('/') || /^[A-Za-z]:/.test(phases[0][1]));
  assert.ok(phases.every(parts => parts[2] === 'false'));
  assert.match(result.output, /doctor finished; exit=0/);
  assert.match(result.output, /ping finished; exit=0/);
  assert.match(result.output, /desktop finished; exit=0/);
  assert.match(result.output, /method=file/);
  assert.ok(!result.output.includes('NEVER_PRINT_LOCK_ID'));
});

test('a launcher exit failure is reported and stops subsequent phases', async t => {
  const f = await setup(t, 'exit-failure');
  const result = await f.run();
  assert.equal(result.code, 7, result.output);
  assert.match(result.output, /doctor finished; exit=7/);
  assert.match(result.output, /fixture failure/);
  assert.match(result.output, /127\.0\.0\.1:1234\/\[redacted H2 access key\]/);
  assert.ok(!result.output.includes('0123456789abcdef0123456789abcdef0123456789abc'));
  const logDirectory = (await readdir(path.join(f.root, 'target'))).find(name => name.startsWith('macos-launcher-smoke.'));
  const stderr = await readFile(path.join(f.root, 'target', logDirectory, 'doctor.stderr'), 'utf8');
  assert.ok(!stderr.includes('0123456789abcdef0123456789abcdef0123456789abc'));
  assert.equal((await readFile(path.join(f.data, 'phase-paths'), 'utf8')).trim().split(/\r?\n/).length, 1);
});

test('a doctor token leak in stderr fails and redacts both console and captured files', async t => {
  const f = await setup(t, 'token-leak');
  const result = await f.run();
  assert.notEqual(result.code, 0);
  assert.match(result.output, /exposed its harness token/);
  assert.ok(!result.output.includes(token));
  const logDirectory = (await readdir(path.join(f.root, 'target'))).find(name => name.startsWith('macos-launcher-smoke.'));
  const stderr = await readFile(path.join(f.root, 'target', logDirectory, 'doctor.stderr'), 'utf8');
  assert.ok(!stderr.includes(token));
});

test('a ready doctor opening the wrong directory is rejected', async t => {
  const f = await setup(t, 'wrong-directory');
  const result = await f.run();
  assert.notEqual(result.code, 0);
  assert.match(result.output, /requested shared data directory/);
});

for (const scenario of ['ping-error', 'ping-null']) {
  test('rejects unsuccessful or malformed MCP ping: ' + scenario, async t => {
    const f = await setup(t, scenario);
    const result = await f.run();
    assert.notEqual(result.code, 0);
    assert.match(result.output, /expected successful response/);
  });
}

test('desktop must create a fresh valid marker and an existing marker is preserved', async t => {
  const f = await setup(t, 'missing-marker');
  const result = await f.run();
  assert.notEqual(result.code, 0);
  assert.match(result.output, /without a new release-smoke.ok/);
  await writeFile(path.join(f.data, 'release-smoke.ok'), 'preserved prior marker');
  const stale = await f.run();
  assert.notEqual(stale.code, 0);
  assert.match(stale.output, /would be stale; it was preserved/);
  assert.equal(await readFile(path.join(f.data, 'release-smoke.ok'), 'utf8'), 'preserved prior marker');
});

test('marker appearance does not bypass awaiting the actual desktop process exit', async t => {
  const f = await setup(t, 'marker-before-exit');
  const result = await f.run(['--timeout-seconds', '2', '--diagnostic-after-seconds', '1']);
  assert.equal(result.code, 124, result.output);
  assert.match(result.output, /desktop exceeded its 2-second deadline/);
  assert.match(result.output, /fixture-thread-dump/);
  assert.match(result.output, /desktop finished; exit=124/);
  assert.ok(result.elapsed < 15000, result.output);
});

test('startup and a stuck jcmd are independently bounded', async t => {
  const f = await setup(t, 'timeout');
  await writeFile(path.join(f.javaHome, 'bin/jcmd'), '#!/usr/bin/env bash\necho diagnostic-started\nexec sleep 30\n');
  const result = await f.run(['--timeout-seconds', '2', '--diagnostic-after-seconds', '1']);
  assert.equal(result.code, 124, result.output);
  assert.match(result.output, /doctor exceeded its 2-second deadline/);
  assert.match(result.output, /thread diagnostic exceeded its 10-second deadline/);
  assert.ok(result.elapsed < 18000, 'Helper did not bound its diagnostic: ' + result.elapsed);
});
