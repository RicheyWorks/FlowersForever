// Private, unpublished preview transport. Requires Node 24+ and a pre-created draft.
// This helper only GETs repository/release/asset metadata and POSTs new release assets.
// It never creates, edits, publishes, or deletes a release, tag, or existing asset.
// A partial upload is retained: reruns require a fresh draft rather than clobbering it.
//
// All commands: GH_TOKEN, GITHUB_REPOSITORY, GITHUB_SHA, PREVIEW_RELEASE_ID,
// VERSION, GITHUB_RUN_ID; optional GITHUB_API_URL (and GITHUB_SERVER_URL for GHES).
// validate performs metadata-only preflight before building.
// publish-inputs outputs manifest_asset_id, manifest_sha256, jar_sha256.
// Other commands require VERIFIED_INPUTS_ASSET_ID and VERIFIED_INPUTS_SHA256.
// publish-completion outputs completion_asset_id, completion_sha256, checksum_asset_id.

import { createHash } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { appendFile, lstat, mkdir, readFile, readdir, realpath, unlink } from 'node:fs/promises';
import path from 'node:path';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { pathToFileURL } from 'node:url';

const MAX_JSON = 1024 * 1024;
const MAX_MANIFEST = 64 * 1024;
const MAX_ASSET = 2 * 1024 * 1024 * 1024 - 1;
const HASH = /^[a-f0-9]{64}$/;
const SHA = /^[a-f0-9]{40}$/;
const TYPES = ['msi', 'dmg'];

function requireValue(condition, message) {
  if (!condition) throw new Error(message);
}

function positiveId(value, name) {
  requireValue(/^[1-9][0-9]*$/.test(String(value)), name + ' must be a positive integer.');
  const id = Number(value);
  requireValue(Number.isSafeInteger(id), name + ' is outside the supported range.');
  return id;
}

function digest(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

export function configuration(env) {
  requireValue(typeof env.GH_TOKEN === 'string' && env.GH_TOKEN.length > 0, 'GH_TOKEN is required.');
  requireValue(/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(env.GITHUB_REPOSITORY || ''), 'Invalid GITHUB_REPOSITORY.');
  requireValue(env.GITHUB_REPOSITORY.split('/').every(part => part !== '.' && part !== '..'), 'Invalid GITHUB_REPOSITORY.');
  requireValue(SHA.test(env.GITHUB_SHA || ''), 'GITHUB_SHA must be the full lowercase source SHA.');
  requireValue(/^[0-9]+\.[0-9]+\.[0-9]+$/.test(env.VERSION || ''), 'VERSION must be x.y.z.');
  const api = new URL(env.GITHUB_API_URL || 'https://api.github.com');
  const server = new URL(env.GITHUB_SERVER_URL || 'https://github.com');
  requireValue(api.protocol === 'https:' && !api.username && !api.password && !api.search && !api.hash,
    'Only a trusted HTTPS GitHub API URL is supported.');
  const cloud = api.origin === 'https://api.github.com' && /^\/?$/.test(api.pathname);
  const enterprise = server.protocol === 'https:' && !server.username && !server.password
    && server.hostname !== 'github.com' && api.origin === server.origin && /^\/api\/v3\/?$/.test(api.pathname);
  requireValue(cloud || enterprise, 'GITHUB_API_URL must match GitHub.com or the GitHub Enterprise server.');
  return {
    token: env.GH_TOKEN, repository: env.GITHUB_REPOSITORY, sourceSha: env.GITHUB_SHA,
    version: env.VERSION, releaseId: positiveId(env.PREVIEW_RELEASE_ID, 'PREVIEW_RELEASE_ID'),
    runId: String(positiveId(env.GITHUB_RUN_ID, 'GITHUB_RUN_ID')),
    apiBase: api.href.replace(/\/$/, ''),
    uploadBase: cloud ? 'https://uploads.github.com' : server.origin + '/api/uploads',
    manifestId: env.VERIFIED_INPUTS_ASSET_ID, manifestHash: env.VERIFIED_INPUTS_SHA256,
    output: env.GITHUB_OUTPUT,
  };
}

async function boundedBytes(response, limit) {
  const chunks = [];
  let length = 0;
  requireValue(response.body, 'GitHub returned an empty response body.');
  const reader = response.body.getReader();
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      length += value.length;
      requireValue(length <= limit, 'GitHub response exceeds its size limit.');
      chunks.push(Buffer.from(value));
    }
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  } finally {
    reader.releaseLock();
  }
  return Buffer.concat(chunks, length);
}

function json(bytes) {
  try { return JSON.parse(bytes.toString('utf8')); }
  catch { throw new Error('GitHub returned invalid JSON.'); }
}

class Transport {
  constructor(config, fetchImpl) {
    this.config = config;
    this.fetch = fetchImpl;
    this.repoPath = '/repos/' + config.repository;
  }

  async request(url, options, purpose) {
    try {
      return await this.fetch(url, {
        redirect: 'manual', signal: AbortSignal.timeout(10 * 60 * 1000), ...options,
      });
    } catch {
      throw new Error('GitHub request failed: ' + purpose + '.');
    }
  }

  headers(accept = 'application/vnd.github+json') {
    return { Accept: accept, Authorization: 'Bearer ' + this.config.token,
      'X-GitHub-Api-Version': '2022-11-28' };
  }

  async get(route, allowMissing = false) {
    const response = await this.request(this.config.apiBase + route,
      { method: 'GET', headers: this.headers() }, 'read metadata');
    if (allowMissing && response.status === 404) {
      await response.body?.cancel();
      return null;
    }
    requireValue(response.status === 200, 'GitHub metadata request returned HTTP ' + response.status + '.');
    return json(await boundedBytes(response, MAX_JSON));
  }

  async guard() {
    const repo = await this.get(this.repoPath);
    requireValue(repo.private === true, 'Preview transport requires a private repository.');
    const release = await this.get(this.repoPath + '/releases/' + this.config.releaseId);
    requireValue(release.id === this.config.releaseId, 'Release ID does not match.');
    requireValue(release.draft === true && release.prerelease === true,
      'Preview transport requires an unpublished prerelease draft.');
    requireValue(release.target_commitish === this.config.sourceSha, 'Draft source SHA does not match.');
    requireValue(/^preview-[A-Za-z0-9][A-Za-z0-9._-]{0,150}$/.test(release.tag_name || ''),
      'Draft tag name must use a safe preview- prefix.');
    const tag = await this.get(this.repoPath + '/git/ref/tags/' + encodeURIComponent(release.tag_name), true);
    requireValue(tag === null, 'The preview draft must not have a published git tag.');
    return release;
  }

  async assets() {
    const assets = [];
    for (let page = 1; page <= 11; page++) {
      const batch = await this.get(this.repoPath + '/releases/' + this.config.releaseId
        + '/assets?per_page=100&page=' + page);
      requireValue(Array.isArray(batch), 'Invalid release asset listing.');
      assets.push(...batch);
      requireValue(assets.length <= 1000, 'Release asset listing exceeds its limit.');
      if (batch.length < 100) return assets;
    }
    throw new Error('Release asset listing exceeds its limit.');
  }

  async binary(asset) {
    let url = this.config.apiBase + this.repoPath + '/releases/assets/' + asset.id;
    let headers = this.headers('application/octet-stream');
    for (let redirects = 0; redirects <= 5; redirects++) {
      const response = await this.request(url, { method: 'GET', headers }, 'download asset');
      if (response.status === 200) return response;
      if ([301, 302, 303, 307, 308].includes(response.status)) {
        const location = response.headers.get('location');
        requireValue(location, 'Asset redirect has no location.');
        const next = new URL(location, url);
        requireValue(next.protocol === 'https:' && !next.username && !next.password,
          'Asset redirect must use HTTPS without embedded credentials.');
        await response.body?.cancel();
        url = next.href;
        // Signed blob URLs need no GitHub token. Strip it on EVERY redirect.
        headers = { Accept: 'application/octet-stream' };
      } else {
        throw new Error('GitHub asset download returned HTTP ' + response.status + '.');
      }
    }
    throw new Error('Asset download exceeded its redirect limit.');
  }

  async upload(name, source, expected) {
    await this.guard();
    const existing = await this.assets();
    requireValue(!existing.some(asset => asset.name === name), 'Asset already exists; use a fresh preview draft: ' + name);
    const url = this.config.uploadBase + this.repoPath + '/releases/' + this.config.releaseId
      + '/assets?name=' + encodeURIComponent(name);
    const body = typeof source === 'function' ? source() : source;
    let response;
    try {
      response = await this.request(url, {
        method: 'POST', headers: { ...this.headers(), 'Content-Type': 'application/octet-stream',
          'Content-Length': String(expected.size) }, body, duplex: 'half',
      }, 'upload new asset');
    } finally {
      if (typeof body.destroy === 'function') body.destroy();
    }
    requireValue(response.status === 201, 'GitHub asset upload returned HTTP ' + response.status + '.');
    const asset = json(await boundedBytes(response, MAX_JSON));
    requireValue(asset.name === name && asset.state === 'uploaded' && asset.size === expected.size,
      'Uploaded asset metadata does not match the file.');
    positiveId(asset.id, 'Uploaded asset ID');
    if (asset.digest != null) requireValue(asset.digest === 'sha256:' + expected.sha256, 'Uploaded asset digest does not match.');
    return { id: asset.id, name, size: expected.size, sha256: expected.sha256 };
  }
}

class Workspace {
  constructor(root) { this.root = root; }

  async file(relative, createParents = false) {
    const parts = relative.split('/');
    requireValue(parts.every(part => part && part !== '.' && part !== '..' && !part.includes('\\')),
      'Unsafe workspace path.');
    let current = this.root;
    for (let index = 0; index < parts.length; index++) {
      current = path.join(current, parts[index]);
      let info;
      try { info = await lstat(current); }
      catch (error) {
        if (error.code !== 'ENOENT') throw error;
        if (index < parts.length - 1) {
          requireValue(createParents, 'Required workspace directory is missing.');
          await mkdir(current);
          info = await lstat(current);
        }
      }
      requireValue(!info?.isSymbolicLink(), 'Workspace symlinks are not allowed.');
      if (index < parts.length - 1) requireValue(info?.isDirectory(), 'Workspace parent must be a directory.');
      else if (info) requireValue(info.isFile(), 'Workspace artifact must be a regular file.');
    }
    return current;
  }

  async describe(relative) {
    const file = await this.file(relative);
    const info = await lstat(file);
    requireValue(info.isFile() && info.size > 0 && info.size <= MAX_ASSET, 'Invalid artifact size.');
    const hash = createHash('sha256');
    let size = 0;
    for await (const chunk of createReadStream(file)) { size += chunk.length; hash.update(chunk); }
    requireValue(size === info.size, 'Artifact changed while hashing.');
    return { file, size, sha256: hash.digest('hex') };
  }

  async emptyDirectory(relative) {
    // Resolve a fixed sentinel name to apply the same parent/symlink checks.
    const sentinel = await this.file(relative + '/.preview-check', true);
    const directory = path.dirname(sentinel);
    requireValue((await readdir(directory)).length === 0, 'Preview destination must be empty: ' + relative);
    return directory;
  }

  async download(transport, descriptor, relative) {
    const file = await this.file(relative, true);
    const response = await transport.binary(descriptor);
    const hash = createHash('sha256');
    let size = 0;
    let created = false;
    const output = createWriteStream(file, { flags: 'wx' });
    output.once('open', () => { created = true; });
    try {
      requireValue(response.body, 'Asset download has no body.');
      const meter = new Transform({ transform(chunk, encoding, callback) {
        size += chunk.length;
        if (size > descriptor.size) return callback(new Error('Downloaded asset exceeds its declared size.'));
        hash.update(chunk);
        callback(null, chunk);
      } });
      await pipeline(Readable.fromWeb(response.body), meter, output);
      requireValue(size === descriptor.size && hash.digest('hex') === descriptor.sha256,
        'Downloaded asset hash or size does not match.');
    } catch (error) {
      output.destroy();
      if (created) await unlink(file).catch(() => {});
      throw error;
    }
  }
}

function identity(config, kind) {
  return { schemaVersion: 1, kind, repository: config.repository, releaseId: config.releaseId,
    sourceSha: config.sourceSha, version: config.version, runId: config.runId };
}

function validateIdentity(value, config, kind) {
  requireValue(value && typeof value === 'object' && !Array.isArray(value), 'Invalid preview manifest.');
  for (const [key, expected] of Object.entries(identity(config, kind))) {
    requireValue(value[key] === expected, 'Preview manifest ' + key + ' does not match.');
  }
}

function inputNames(config) {
  return ['flowerfarm-manager-' + config.version + '.jar', 'bom.json', 'bom.xml', 'THIRD-PARTY.txt'];
}

function installerName(config, type) { return 'FlowersForever-' + config.version + '.' + type; }

function validateDescriptor(value, name, listing) {
  requireValue(value && value.name === name, 'Unexpected preview artifact name.');
  positiveId(value.id, 'Asset ID');
  requireValue(Number.isSafeInteger(value.size) && value.size > 0 && value.size <= MAX_ASSET && HASH.test(value.sha256 || ''),
    'Invalid preview artifact digest or size.');
  const matches = listing.filter(asset => asset.id === value.id && asset.name === name);
  requireValue(matches.length === 1 && matches[0].state === 'uploaded' && matches[0].size === value.size,
    'Asset does not belong to this preview draft or its metadata changed.');
  requireValue(listing.filter(asset => asset.name === name).length === 1, 'Duplicate preview asset name.');
  if (matches[0].digest != null) requireValue(matches[0].digest === 'sha256:' + value.sha256, 'Release asset digest does not match.');
  return { id: value.id, name, size: value.size, sha256: value.sha256 };
}

async function readManifest(transport, asset, expectedHash) {
  requireValue(asset.size > 0 && asset.size <= MAX_MANIFEST, 'Preview manifest exceeds its size limit.');
  const response = await transport.binary(asset);
  const bytes = await boundedBytes(response, MAX_MANIFEST);
  requireValue(bytes.length === asset.size && digest(bytes) === expectedHash, 'Preview manifest hash or size does not match.');
  return json(bytes);
}

async function verifiedInputs(config, transport) {
  const id = positiveId(config.manifestId, 'VERIFIED_INPUTS_ASSET_ID');
  requireValue(HASH.test(config.manifestHash || ''), 'VERIFIED_INPUTS_SHA256 must be a SHA-256 digest.');
  const listing = await transport.assets();
  const asset = listing.find(item => item.id === id && item.name === 'verified-inputs.json');
  requireValue(asset, 'Verified input manifest is missing from this draft.');
  const manifestDescriptor = validateDescriptor({ ...asset, sha256: config.manifestHash }, 'verified-inputs.json', listing);
  const manifest = await readManifest(transport, manifestDescriptor, config.manifestHash);
  validateIdentity(manifest, config, 'verified-inputs');
  const names = inputNames(config);
  requireValue(Array.isArray(manifest.assets) && manifest.assets.length === names.length, 'Verified input manifest must contain exactly four artifacts.');
  const assets = names.map(name => validateDescriptor(manifest.assets.find(item => item.name === name), name, listing));
  requireValue(new Set(assets.map(item => item.id)).size === assets.length, 'Preview asset IDs must be unique.');
  return { manifestDescriptor, assets, listing, jar: assets[0] };
}

async function candidateInputs(config, transport) {
  const inputs = await verifiedInputs(config, transport);
  const assets = [...inputs.assets];
  const records = [];
  for (const type of TYPES) {
    const name = 'installer-' + type + '.json';
    const matches = inputs.listing.filter(item => item.name === name);
    requireValue(matches.length === 1, 'Installer record is missing or duplicated: ' + name);
    const metadata = matches[0];
    requireValue(metadata.state === 'uploaded' && metadata.size > 0 && metadata.size <= MAX_MANIFEST,
      'Invalid installer record metadata.');
    positiveId(metadata.id, 'Installer record asset ID');
    const bytes = await boundedBytes(await transport.binary(metadata), MAX_MANIFEST);
    requireValue(bytes.length === metadata.size, 'Installer record size does not match.');
    const recordDescriptor = validateDescriptor({ ...metadata, sha256: digest(bytes) }, name, inputs.listing);
    const record = json(bytes);
    validateIdentity(record, config, 'installer');
    requireValue(record.type === type && record.inputJarSha256 === inputs.jar.sha256,
      'Installer type or verified JAR hash does not match.');
    assets.push(validateDescriptor(record.artifact, installerName(config, type), inputs.listing));
    records.push(recordDescriptor);
  }
  requireValue(new Set([...assets, ...records, inputs.manifestDescriptor].map(item => item.id)).size
    === assets.length + records.length + 1, 'Preview asset IDs must be unique.');
  return { ...inputs, assets, records };
}

async function uploadJson(config, transport, name, value) {
  const bytes = Buffer.from(JSON.stringify(value, null, 2) + '\n');
  requireValue(bytes.length <= MAX_MANIFEST, 'Preview manifest exceeds its size limit.');
  return transport.upload(name, bytes, { size: bytes.length, sha256: digest(bytes) });
}

async function uploadFile(workspace, transport, relative, name) {
  const description = await workspace.describe(relative);
  const uploaded = await transport.upload(name, () => createReadStream(description.file), description);
  const after = await workspace.describe(relative);
  requireValue(after.size === description.size && after.sha256 === description.sha256, 'Artifact changed during upload.');
  return uploaded;
}

async function emit(config, values) {
  if (config.output) await appendFile(config.output,
    Object.entries(values).map(([key, value]) => key + '=' + value + '\n').join(''));
  return values;
}

function validateChecksums(bytes, descriptors) {
  requireValue(bytes.length > 0 && bytes.length <= MAX_MANIFEST, 'Invalid checksum manifest size.');
  const lines = bytes.toString('utf8').trimEnd().split(/\r?\n/);
  requireValue(lines.length === descriptors.length, 'Checksum manifest must contain exactly the six preview artifacts.');
  const seen = new Set();
  for (const line of lines) {
    const match = /^([a-f0-9]{64}) [ *](?:\.\/)?([^/\\]+)$/.exec(line);
    requireValue(match, 'Invalid checksum manifest record.');
    const [, hash, name] = match;
    const descriptor = descriptors.find(item => item.name === name);
    requireValue(descriptor && !seen.has(name) && descriptor.sha256 === hash,
      'Checksum manifest contains an unexpected, duplicate, or mismatched artifact.');
    seen.add(name);
  }
}

export async function runPreviewCommand(arguments_, { env = process.env, cwd = process.cwd(), fetchImpl = globalThis.fetch } = {}) {
  const [command, type] = arguments_;
  requireValue(['validate', 'publish-inputs', 'download-jar', 'publish-installer', 'collect-candidate', 'publish-completion'].includes(command),
    'Unknown preview transport command.');
  requireValue(arguments_.length === (command === 'publish-installer' ? 2 : 1), 'Unexpected preview transport arguments.');
  if (command === 'publish-installer') requireValue(TYPES.includes(type), 'Installer type must be msi or dmg.');
  const config = configuration(env);
  const transport = new Transport(config, fetchImpl);
  const workspace = new Workspace(await realpath(cwd));
  await transport.guard();
  if (command === 'validate') return {};

  if (command === 'publish-inputs') {
    const names = inputNames(config);
    const listing = await transport.assets();
    requireValue(!listing.some(item => [...names, 'verified-inputs.json'].includes(item.name)),
      'Input assets already exist; use a fresh preview draft.');
    const artifacts = names.map((name, index) => ({ name,
      relative: index === 0 ? 'target/' + name : 'target/release-metadata/' + name }));
    // Preflight every local file before uploading any of them.
    for (const artifact of artifacts) await workspace.describe(artifact.relative);
    const assets = [];
    for (const artifact of artifacts) assets.push(await uploadFile(workspace, transport, artifact.relative, artifact.name));
    const manifest = await uploadJson(config, transport, 'verified-inputs.json', {
      ...identity(config, 'verified-inputs'), assets,
    });
    return emit(config, { manifest_asset_id: manifest.id, manifest_sha256: manifest.sha256, jar_sha256: assets[0].sha256 });
  }

  if (command === 'download-jar') {
    const inputs = await verifiedInputs(config, transport);
    await workspace.emptyDirectory('target/jpackage-input');
    await workspace.download(transport, inputs.jar, 'target/jpackage-input/' + inputs.jar.name);
    return { jar_sha256: inputs.jar.sha256 };
  }

  if (command === 'publish-installer') {
    const inputs = await verifiedInputs(config, transport);
    const jar = await workspace.describe('target/jpackage-input/' + inputs.jar.name);
    requireValue(jar.size === inputs.jar.size && jar.sha256 === inputs.jar.sha256, 'Packaged input JAR does not match the Linux-verified JAR.');
    const name = installerName(config, type);
    const expected = await workspace.file('dist/' + name);
    const files = (await readdir(path.dirname(expected))).filter(file => file.toLowerCase().endsWith('.' + type));
    requireValue(files.length === 1 && files[0] === name, 'Expected exactly one installer with the verified version.');
    requireValue(!inputs.listing.some(item => item.name === name || item.name === 'installer-' + type + '.json'),
      'Installer assets already exist; use a fresh preview draft.');
    const artifact = await uploadFile(workspace, transport, 'dist/' + name, name);
    const record = await uploadJson(config, transport, 'installer-' + type + '.json', {
      ...identity(config, 'installer'), type, inputJarSha256: inputs.jar.sha256, artifact,
    });
    return { installer_asset_id: artifact.id, installer_sha256: artifact.sha256, record_asset_id: record.id };
  }

  const candidate = await candidateInputs(config, transport);
  if (command === 'collect-candidate') {
    await workspace.emptyDirectory('candidate-assets');
    for (const asset of candidate.assets) await workspace.download(transport, asset, 'candidate-assets/' + asset.name);
    return { artifact_count: candidate.assets.length };
  }

  const checksumFile = await workspace.file('candidate-assets/SHA256SUMS.txt');
  const names = await readdir(path.dirname(checksumFile));
  requireValue(names.length === candidate.assets.length + 1 && names.every(name => name === 'SHA256SUMS.txt'
    || candidate.assets.some(asset => asset.name === name)), 'Candidate directory contains unexpected files.');
  for (const asset of candidate.assets) {
    const actual = await workspace.describe('candidate-assets/' + asset.name);
    requireValue(actual.size === asset.size && actual.sha256 === asset.sha256, 'Collected candidate artifact hash does not match.');
  }
  requireValue((await lstat(checksumFile)).size <= MAX_MANIFEST, 'Checksum manifest exceeds its size limit.');
  validateChecksums(await readFile(checksumFile), candidate.assets);
  requireValue(!candidate.listing.some(item => ['SHA256SUMS.txt', 'preview-complete.json'].includes(item.name)),
    'Completion assets already exist; use a fresh preview draft.');
  const checksums = await uploadFile(workspace, transport, 'candidate-assets/SHA256SUMS.txt', 'SHA256SUMS.txt');
  const complete = await uploadJson(config, transport, 'preview-complete.json', {
    ...identity(config, 'preview-complete'), inputJarSha256: candidate.jar.sha256,
    signing: 'Unsigned and not notarized; private preview only.',
    verifiedInputs: candidate.manifestDescriptor, installerRecords: candidate.records,
    assets: [...candidate.assets, checksums],
  });
  await transport.guard();
  return emit(config, { completion_asset_id: complete.id, completion_sha256: complete.sha256, checksum_asset_id: checksums.id });
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  runPreviewCommand(process.argv.slice(2)).then(() => {
    process.stdout.write('Private preview transport completed.\n');
  }).catch(error => {
    process.stderr.write('Private preview transport failed: ' + error.message + '\n');
    process.exitCode = 1;
  });
}
