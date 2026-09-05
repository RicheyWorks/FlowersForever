// Run with Node 24+: node --test scripts/preview-release-assets.test.mjs
// Every network request is handled by an in-memory GitHub API; no remote writes.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, mkdir, readFile, readdir, realpath, rm, symlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { configuration, runPreviewCommand } from './preview-release-assets.mjs';

const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const responseJson = (value, status = 200) => new Response(JSON.stringify(value), { status });

class FakeGitHub {
  private = true;
  release = { id: 34, draft: true, prerelease: true, target_commitish: 'a'.repeat(40), tag_name: 'preview-test-a1' };
  tagStatus = 404;
  files = new Map();
  calls = [];
  nextId = 100;
  redirect = false;
  redirectProtocol = 'https:';
  corruptDownloads = new Set();
  failUploadName;

  add(name, bytes) {
    const data = Buffer.from(bytes);
    const asset = { id: this.nextId++, name, state: 'uploaded', size: data.length, digest: 'sha256:' + hash(data) };
    this.files.set(asset.id, { asset, bytes: data });
    return asset;
  }

  named(name) { return [...this.files.values()].find(file => file.asset.name === name); }

  changeJson(name, mutate) {
    const entry = this.named(name);
    const value = JSON.parse(entry.bytes);
    mutate(value);
    entry.bytes = Buffer.from(JSON.stringify(value));
    entry.asset.size = entry.bytes.length;
    entry.asset.digest = 'sha256:' + hash(entry.bytes);
  }

  fetch = async (url, options = {}) => {
    const parsed = new URL(url);
    const method = options.method || 'GET';
    this.calls.push({ url: parsed, method, headers: options.headers });
    assert.equal(options.redirect, 'manual');
    if (parsed.hostname === 'blob.example.test') {
      assert.equal(options.headers.Authorization, undefined, 'GitHub token must not reach the blob host');
      const entry = this.files.get(Number(parsed.pathname.slice(1)));
      return new Response(this.downloadBytes(entry));
    }
    assert.equal(options.headers.Authorization, 'Bearer fixture-secret-token');
    if (method === 'POST') {
      assert.equal(parsed.origin, 'https://uploads.github.com');
      assert.equal(parsed.pathname, '/repos/RicheyWorks/FlowersForever/releases/34/assets');
      const name = parsed.searchParams.get('name');
      if (name === this.failUploadName) return responseJson({ error: 'fixture upload failure' }, 500);
      if (this.named(name)) return responseJson({ error: 'already exists' }, 422);
      let bytes;
      if (Buffer.isBuffer(options.body)) bytes = options.body;
      else {
        const chunks = [];
        for await (const chunk of options.body) chunks.push(Buffer.from(chunk));
        bytes = Buffer.concat(chunks);
      }
      assert.equal(Number(options.headers['Content-Length']), bytes.length);
      return responseJson(this.add(name, bytes), 201);
    }
    assert.equal(method, 'GET', 'No release edits, publication, or asset deletion are allowed');
    assert.equal(parsed.origin, 'https://api.github.com');
    if (parsed.pathname === '/repos/RicheyWorks/FlowersForever') return responseJson({ private: this.private });
    if (parsed.pathname === '/repos/RicheyWorks/FlowersForever/releases/34') return responseJson(this.release);
    if (parsed.pathname.startsWith('/repos/RicheyWorks/FlowersForever/git/ref/tags/')) {
      return responseJson(this.tagStatus === 200 ? { ref: 'refs/tags/' + this.release.tag_name } : {}, this.tagStatus);
    }
    if (parsed.pathname === '/repos/RicheyWorks/FlowersForever/releases/34/assets') {
      const page = Number(parsed.searchParams.get('page'));
      return responseJson([...this.files.values()].map(file => file.asset).slice((page - 1) * 100, page * 100));
    }
    const match = /\/releases\/assets\/([0-9]+)$/.exec(parsed.pathname);
    if (match) {
      assert.equal(options.headers.Accept, 'application/octet-stream');
      const entry = this.files.get(Number(match[1]));
      if (!entry) return responseJson({}, 404);
      if (this.redirect) return new Response(null, { status: 302,
        headers: { location: this.redirectProtocol + '//blob.example.test/' + entry.asset.id } });
      return new Response(this.downloadBytes(entry));
    }
    throw new Error('Unexpected API route in fixture.');
  };

  downloadBytes(entry) {
    if (!this.corruptDownloads.has(entry.asset.name)) return entry.bytes;
    const corrupted = Buffer.from(entry.bytes);
    corrupted[0] ^= 1;
    return corrupted;
  }
}

async function fixture(t) {
  const temp = await realpath(os.tmpdir());
  const root = await mkdtemp(path.join(temp, 'flowersforever-preview-'));
  t.after(async () => {
    const resolved = await realpath(root);
    assert.ok(resolved.startsWith(temp + path.sep + 'flowersforever-preview-'));
    await rm(resolved, { recursive: true, force: true });
  });
  const api = new FakeGitHub();
  const env = { GH_TOKEN: 'fixture-secret-token', GITHUB_REPOSITORY: 'RicheyWorks/FlowersForever',
    GITHUB_SHA: 'a'.repeat(40), PREVIEW_RELEASE_ID: '34', VERSION: '1.2.0', GITHUB_RUN_ID: '456',
    GITHUB_OUTPUT: path.join(root, 'outputs.txt') };
  const write = async (relative, bytes) => {
    const destination = path.join(root, relative);
    await mkdir(path.dirname(destination), { recursive: true });
    await writeFile(destination, bytes);
  };
  const run = args => runPreviewCommand(args, { env, cwd: root, fetchImpl: api.fetch });
  const publishInputs = async () => {
    await write('target/flowerfarm-manager-1.2.0.jar', Buffer.from([0x50, 0x4b, 0xff, 0x00, 0x80, 0x0a]));
    await write('target/release-metadata/bom.json', '{"fixture":true}');
    await write('target/release-metadata/bom.xml', '<bom/>');
    await write('target/release-metadata/THIRD-PARTY.txt', 'fixture licenses\n');
    const output = await run(['publish-inputs']);
    env.VERIFIED_INPUTS_ASSET_ID = String(output.manifest_asset_id);
    env.VERIFIED_INPUTS_SHA256 = output.manifest_sha256;
    return output;
  };
  const prepareCandidate = async () => {
    await publishInputs();
    await run(['download-jar']);
    for (const type of ['msi', 'dmg']) {
      await write('dist/FlowersForever-1.2.0.' + type, 'fixture ' + type + ' installer');
      await run(['publish-installer', type]);
    }
    await run(['collect-candidate']);
  };
  const writeChecksums = async (binary = true) => {
    const names = (await readdir(path.join(root, 'candidate-assets'))).filter(name => name !== 'SHA256SUMS.txt').sort();
    const lines = [];
    for (const name of names) lines.push(hash(await readFile(path.join(root, 'candidate-assets', name)))
      + (binary ? ' *./' : '  ./') + name);
    await write('candidate-assets/SHA256SUMS.txt', lines.join('\n') + '\n');
  };
  return { root, api, env, write, run, publishInputs, prepareCandidate, writeChecksums };
}

test('validate checks only private draft metadata and creates no assets', async t => {
  const f = await fixture(t);
  assert.deepEqual(await f.run(['validate']), {});
  assert.equal(f.api.calls.length, 3);
  assert.ok(f.api.calls.every(call => call.method === 'GET'));
  assert.equal(f.api.files.size, 0);
});

test('one verified JAR is transported unchanged through both platform records and completion', async t => {
  const f = await fixture(t);
  await f.prepareCandidate();
  assert.equal((await readdir(path.join(f.root, 'candidate-assets'))).length, 6);
  assert.deepEqual(await readFile(path.join(f.root, 'target/flowerfarm-manager-1.2.0.jar')),
    await readFile(path.join(f.root, 'candidate-assets/flowerfarm-manager-1.2.0.jar')));
  await f.writeChecksums();
  const output = await f.run(['publish-completion']);
  assert.ok(output.completion_asset_id > 0);
  const complete = JSON.parse(f.api.named('preview-complete.json').bytes);
  assert.equal(complete.assets.length, 7);
  assert.equal(complete.sourceSha, f.env.GITHUB_SHA);
  assert.equal(complete.version, '1.2.0');
  assert.equal(complete.inputJarSha256, hash(f.api.named('flowerfarm-manager-1.2.0.jar').bytes));
  assert.equal(complete.verifiedInputs.id, Number(f.env.VERIFIED_INPUTS_ASSET_ID));
  assert.equal(f.api.files.size, 11);
  const uploads = f.api.calls.filter(call => call.method === 'POST');
  assert.equal(uploads.length, 11);
  assert.ok(uploads.every(call => call.url.pathname.endsWith('/releases/34/assets')));
  assert.equal(uploads.at(-1).url.searchParams.get('name'), 'preview-complete.json');
  assert.equal(f.api.release.draft, true);
  assert.equal(f.api.tagStatus, 404);
  const outputs = await readFile(f.env.GITHUB_OUTPUT, 'utf8');
  assert.match(outputs, /manifest_asset_id=[0-9]+/);
  assert.match(outputs, /completion_sha256=[a-f0-9]{64}/);
  assert.ok(!outputs.includes(f.env.GH_TOKEN));
});

test('binary 302 asset downloads strip authorization from the blob request', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  f.api.redirect = true;
  await f.run(['download-jar']);
  const blobCalls = f.api.calls.filter(call => call.url.hostname === 'blob.example.test');
  assert.equal(blobCalls.length, 2);
  assert.ok(blobCalls.every(call => call.headers.Authorization === undefined));
});

test('rejects an insecure asset redirect without sending the token or reading the asset', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  f.api.redirect = true;
  f.api.redirectProtocol = 'http:';
  await assert.rejects(f.run(['download-jar']), /redirect must use HTTPS/);
  assert.ok(!f.api.calls.some(call => call.url.hostname === 'blob.example.test'));
});

for (const [name, mutate, message] of [
  ['public repository', f => { f.api.private = false; }, /private repository/],
  ['published release', f => { f.api.release.draft = false; }, /unpublished prerelease draft/],
  ['non-prerelease', f => { f.api.release.prerelease = false; }, /unpublished prerelease draft/],
  ['different source', f => { f.api.release.target_commitish = 'b'.repeat(40); }, /source SHA/],
  ['version tag', f => { f.api.release.tag_name = 'v1.2.0'; }, /preview- prefix/],
  ['existing tag ref', f => { f.api.tagStatus = 200; }, /published git tag/],
  ['unauthorized ref lookup', f => { f.api.tagStatus = 403; }, /HTTP 403/],
]) {
  test('preflight rejects ' + name + ' before uploading anything', async t => {
    const f = await fixture(t);
    mutate(f);
    await assert.rejects(f.run(['publish-inputs']), message);
    assert.ok(f.api.calls.every(call => call.method === 'GET'));
  });
}

test('environment validation rejects source/version/path/API injection', async t => {
  const f = await fixture(t);
  for (const values of [
    { GITHUB_SHA: 'main' }, { VERSION: '../../1.2.0' }, { GITHUB_REPOSITORY: '../FlowersForever' },
    { GITHUB_API_URL: 'https://attacker.example' }, { GITHUB_API_URL: 'http://api.github.com' },
    { PREVIEW_RELEASE_ID: '34/../35' }, { GITHUB_RUN_ID: '0' },
  ]) assert.throws(() => configuration({ ...f.env, ...values }));
});

test('preflight validates every local input before the first upload', async t => {
  const f = await fixture(t);
  await f.write('target/flowerfarm-manager-1.2.0.jar', 'fixture jar');
  await assert.rejects(f.run(['publish-inputs']), /directory is missing|ENOENT/);
  assert.equal(f.api.files.size, 0);
});

test('duplicate and partial inputs are retained and require a fresh draft', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  const before = f.api.files.size;
  await assert.rejects(f.run(['publish-inputs']), /already exist.*fresh preview draft/);
  assert.equal(f.api.files.size, before);
  assert.ok(f.api.calls.every(call => ['GET', 'POST'].includes(call.method)));
});

for (const [field, value] of [['sourceSha', 'b'.repeat(40)], ['version', '9.9.9'], ['runId', '789'], ['releaseId', 99]]) {
  test('rejects verified manifest with mismatched ' + field, async t => {
    const f = await fixture(t);
    await f.publishInputs();
    f.api.changeJson('verified-inputs.json', manifest => { manifest[field] = value; });
    f.env.VERIFIED_INPUTS_SHA256 = hash(f.api.named('verified-inputs.json').bytes);
    await assert.rejects(f.run(['download-jar']), /manifest .* does not match/);
  });
}

test('known manifest ID and hash must match the current draft', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  const actualId = f.env.VERIFIED_INPUTS_ASSET_ID;
  f.env.VERIFIED_INPUTS_ASSET_ID = '999999';
  await assert.rejects(f.run(['download-jar']), /missing from this draft/);
  f.env.VERIFIED_INPUTS_ASSET_ID = actualId;
  f.env.VERIFIED_INPUTS_SHA256 = '0'.repeat(64);
  await assert.rejects(f.run(['download-jar']), /digest does not match/);
});

test('oversized input manifests are rejected before download', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  f.api.named('verified-inputs.json').asset.size = 65537;
  await assert.rejects(f.run(['download-jar']), /manifest exceeds its size limit/);
});

test('manifest file names cannot escape the allowed artifact names', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  f.api.changeJson('verified-inputs.json', manifest => { manifest.assets[0].name = '../farm-data.zip'; });
  f.env.VERIFIED_INPUTS_SHA256 = hash(f.api.named('verified-inputs.json').bytes);
  await assert.rejects(f.run(['download-jar']), /Unexpected preview artifact name/);
});

test('download hash corruption is rejected and its newly-created partial file removed', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  f.api.corruptDownloads.add('flowerfarm-manager-1.2.0.jar');
  await assert.rejects(f.run(['download-jar']), /hash or size does not match/);
  assert.deepEqual(await readdir(path.join(f.root, 'target/jpackage-input')), []);
});

test('existing local destination files are never overwritten', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  await f.write('target/jpackage-input/flowerfarm-manager-1.2.0.jar', 'preserved existing artifact');
  await assert.rejects(f.run(['download-jar']), /destination must be empty/);
  assert.equal(await readFile(path.join(f.root, 'target/jpackage-input/flowerfarm-manager-1.2.0.jar'), 'utf8'), 'preserved existing artifact');
});

test('installer publication rejects a substituted packaged JAR', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  await f.run(['download-jar']);
  await f.write('target/jpackage-input/flowerfarm-manager-1.2.0.jar', 'different jar');
  await f.write('dist/FlowersForever-1.2.0.msi', 'installer');
  const before = f.api.files.size;
  await assert.rejects(f.run(['publish-installer', 'msi']), /does not match the Linux-verified JAR/);
  assert.equal(f.api.files.size, before);
});

test('installer publication requires exactly one file of the requested native type', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  await f.run(['download-jar']);
  await f.write('dist/FlowersForever-1.2.0.msi', 'installer');
  await f.write('dist/old.msi', 'old installer');
  await assert.rejects(f.run(['publish-installer', 'msi']), /exactly one installer/);
});

test('candidate collection rejects an installer built from another JAR', async t => {
  const f = await fixture(t);
  await f.prepareCandidate();
  f.api.changeJson('installer-msi.json', record => { record.inputJarSha256 = '0'.repeat(64); });
  await assert.rejects(f.run(['collect-candidate']), /verified JAR hash does not match/);
});

test('completion cannot appear when candidate files or checksums have changed', async t => {
  const f = await fixture(t);
  await f.prepareCandidate();
  await f.writeChecksums();
  await f.write('candidate-assets/bom.json', 'tampered');
  await assert.rejects(f.run(['publish-completion']), /candidate artifact hash does not match/);
  assert.equal(f.api.named('SHA256SUMS.txt'), undefined);
  assert.equal(f.api.named('preview-complete.json'), undefined);
});

test('completion rejects checksum traversal, duplicates, self entries, and wrong digests', async t => {
  const f = await fixture(t);
  await f.prepareCandidate();
  await f.writeChecksums();
  const original = await readFile(path.join(f.root, 'candidate-assets/SHA256SUMS.txt'), 'utf8');
  const lines = original.trimEnd().split('\n');
  const variants = [
    original.replace('*./', '*../'),
    [lines[0], ...lines.slice(0, -1)].join('\n'),
    original.replace(/\*\.\/[^\n]+/, '*./SHA256SUMS.txt'),
    original.replace(/^[a-f0-9]{64}/, '0'.repeat(64)),
  ];
  for (const value of variants) {
    await f.write('candidate-assets/SHA256SUMS.txt', value);
    await assert.rejects(f.run(['publish-completion']), /checksum manifest record|Checksum manifest contains/);
  }
  assert.equal(f.api.named('preview-complete.json'), undefined);
});

test('ordinary text-mode checksum records are also accepted', async t => {
  const f = await fixture(t);
  await f.prepareCandidate();
  await f.writeChecksums(false);
  await f.run(['publish-completion']);
  assert.ok(f.api.named('preview-complete.json'));
});

test('workspace directory symlinks cannot redirect downloads outside the checkout', async t => {
  const f = await fixture(t);
  await f.publishInputs();
  const outside = path.join(f.root, 'outside');
  await mkdir(outside);
  await symlink(outside, path.join(f.root, 'target/jpackage-input'), process.platform === 'win32' ? 'junction' : 'dir');
  await assert.rejects(f.run(['download-jar']), /symlinks are not allowed/);
  assert.deepEqual(await readdir(outside), []);
});

test('failed uploads never retry, delete, or write a completion marker', async t => {
  const f = await fixture(t);
  await f.prepareCandidate();
  await f.writeChecksums();
  f.api.failUploadName = 'preview-complete.json';
  await assert.rejects(f.run(['publish-completion']), /HTTP 500/);
  assert.ok(f.api.named('SHA256SUMS.txt'));
  assert.equal(f.api.named('preview-complete.json'), undefined);
  assert.equal(f.api.calls.filter(call => call.method === 'POST'
    && call.url.searchParams.get('name') === 'preview-complete.json').length, 1);
  assert.ok(f.api.calls.every(call => ['GET', 'POST'].includes(call.method)));
});
