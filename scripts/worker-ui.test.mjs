// Run with Node 18+: node --test scripts/worker-ui.test.mjs
// Executes the shipped worker.js against its real HTML form contract. The small
// DOM adapter isolates storage/network faults without adding browser dependencies.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";

const script = readFileSync(new URL("../src/main/resources/static/worker/worker.js", import.meta.url), "utf8");
const html = readFileSync(new URL("../src/main/resources/static/worker/index.html", import.meta.url), "utf8");
const queueKey = "flowersforever.worker.pending.v1";
const draftKey = "flowersforever.worker.drafts.v1";
const farmDate = "2026-09-05";

class Element {
  constructor(attributes = {}) {
    Object.assign(this, { value: "", textContent: "", hidden: false, disabled: false,
      children: [], dataset: {}, attributes, listeners: {}, classList: { toggle() {} } });
  }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; }
  addEventListener(event, listener) { (this.listeners[event] ??= []).push(listener); }
  async fire(event) {
    for (const listener of this.listeners[event] || []) await listener({ preventDefault() {} });
  }
  setAttribute(name, value) { this.attributes[name] = value; }
  focus() { this.focused = true; }
  scrollIntoView() {}
  querySelector(selector) {
    assert.equal(selector, "button[type=submit]");
    return this.submitButton;
  }
  set innerHTML(_value) { throw new Error("User input must render as text, never HTML."); }
}

function attrs(source) {
  return Object.fromEntries([...source.matchAll(/([\w-]+)="([^"]*)"/g)].map(match => [match[1], match[2]]));
}

function buildDocument() {
  const elements = new Map();
  for (const tag of html.matchAll(/<[\w-]+\b([^>]*)>/g)) {
    const attributes = attrs(tag[1]);
    if (attributes.id) elements.set(attributes.id, new Element(attributes));
  }
  for (const form of html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form>/g)) {
    const element = elements.get(attrs(form[1]).id);
    if (!element) continue; // Forms inside repeated task/order templates have no static id.
    element.elements = {};
    element.submitButton = new Element();
    Object.defineProperty(element.elements, Symbol.iterator, {
      value: function* () { yield* Object.values(this); yield element.submitButton; }
    });
    for (const control of form[2].matchAll(/<(input|textarea|select)\b([^>]*)(?:>([\s\S]*?)<\/\1>|>)/g)) {
      const attributes = attrs(control[2]);
      if (!attributes.name) continue;
      const field = elements.get(attributes.id) || new Element(attributes);
      field.value = attributes.value || (control[1] === "select"
        ? attrs((control[3] || "").match(/<option\b([^>]*)>/)?.[1] || "").value || "" : "");
      field.initialValue = field.value;
      element.elements[attributes.name] = field;
    }
    element.reset = () => Object.values(element.elements).forEach(field => { field.value = field.initialValue; });
  }
  const closeButtons = [...html.matchAll(/<button\b([^>]*data-close-capture[^>]*)>/g)].map(match => {
    const element = new Element(attrs(match[1]));
    element.dataset.closeCapture = element.attributes["data-close-capture"];
    return element;
  });
  const document = {
    querySelector(selector) {
      const [id, child] = selector.split(" ");
      assert.ok(id.startsWith("#"), `Unexpected selector ${selector}`);
      const element = elements.get(id.slice(1));
      assert.ok(element, `worker.js references missing HTML element ${selector}`);
      if (child) {
        assert.equal(child, "small");
        return element.small ??= new Element();
      }
      return element;
    },
    querySelectorAll(selector) {
      assert.equal(selector, "[data-close-capture]");
      return closeButtons;
    },
    createElement() { return new Element(); }
  };
  return { document, elements, closeButtons };
}

function server() {
  return { mode: "online", receipts: new Map(), attempts: [], nextKey: 1 };
}

async function boot({ storage = new Map(), backend = server(), denyQueueWrites = false, denyDraftClear = false } = {}) {
  const dom = buildDocument();
  const navigator = { onLine: true };
  const fetch = async (url, options = {}) => {
    if (options.method === "POST") {
      const body = JSON.parse(options.body);
      backend.attempts.push(body);
      if (backend.waitForResponse) await backend.waitForResponse;
      if (backend.mode === "offline") throw new TypeError("Connection unavailable");
      if (backend.mode === "reject") return response(400, { error: "Observation is required." });
      const replayed = backend.receipts.has(body.operationKey);
      if (!replayed) backend.receipts.set(body.operationKey, { ...body, id: backend.receipts.size + 1, summary: body.observation });
      if (backend.mode === "response-lost") throw new TypeError("Response interrupted after commit");
      if (backend.mode === "invalid-json") return { ok: true, json: async () => { throw new SyntaxError("Truncated JSON"); } };
      return response(replayed ? 200 : 201, { replayed, result: backend.receipts.get(body.operationKey) });
    }
    if (backend.mode === "offline") throw new TypeError("Connection unavailable");
    if (url.startsWith("/api/worker/today")) return response(200, { date: farmDate, tasks: [], openTaskTotal: 0 });
    if (url.startsWith("/api/worker/orders")) return response(200, { orders: [], dueOrderTotal: 0 });
    if (url.startsWith("/api/worker/inventory")) return response(200, { items: [], matchingTotal: 0 });
    if (url.startsWith("/api/worker/field-notes")) return response(200, { notes: [...backend.receipts.values()] });
    throw new Error(`Unexpected request ${url}`);
  };
  const response = (status, body) => ({ ok: status < 400, status, json: async () => body });
  class FormData {
    constructor(form) { this.values = new Map(Object.entries(form.elements).map(([name, field]) => [name, field.value])); }
    get(name) { return this.values.get(name); }
    [Symbol.iterator]() { return this.values[Symbol.iterator](); }
  }
  vm.runInNewContext(script, {
    document: dom.document, navigator, window: new Element(), FormData, fetch, TypeError,
    crypto: { randomUUID: () => `test-operation-${backend.nextKey++}` },
    localStorage: {
      getItem: key => storage.get(key) ?? null,
      setItem: (key, value) => {
        if (denyQueueWrites && key === queueKey) throw new Error("Storage quota exceeded");
        if (denyDraftClear && key === draftKey && value === "{}") throw new Error("Draft cleanup failed");
        storage.set(key, value);
      }
    }
  }, { filename: "worker.js" });
  // Startup includes dependent reads and automatic replay; drain those promises.
  for (let turn = 0; turn < 3; turn++) await new Promise(resolve => setImmediate(resolve));
  const get = id => dom.elements.get(id);
  return { ...dom, get, storage, backend, navigator,
    async fillNote(values) {
      for (const [name, value] of Object.entries(values)) get("field-note-form").elements[name].value = value;
      await get("field-note-form").fire("input");
    },
    pending: () => JSON.parse(storage.get(queueKey) || "[]")
  };
}

test("farm date initializes both capture forms; drafts survive close, form switch and reload", async () => {
  const page = await boot();
  assert.equal(page.get("field-note-date").value, farmDate);
  assert.equal(page.get("harvest-date").value, farmDate);
  await page.get("add-field-note").fire("click");
  assert.equal(page.get("field-note-section").hidden, false);
  await page.fillNote({ observation: "Stems leaning after rain", observedOn: "2026-09-03", bedOrField: "Bed 4" });
  await page.closeButtons.find(button => button.dataset.closeCapture === "note").fire("click");
  assert.equal(page.get("field-note-section").hidden, true);
  await page.get("add-harvest").fire("click");
  await page.get("add-field-note").fire("click");
  assert.equal(page.get("field-note-form").elements.observation.value, "Stems leaning after rain");
  const reload = await boot({ storage: page.storage, backend: page.backend });
  assert.equal(reload.get("field-note-date").value, "2026-09-03");
  assert.equal(reload.get("field-note-form").elements.bedOrField.value, "Bed 4");
  assert.match(reload.get("field-note-message").textContent, /draft is restored/);
  assert.equal(page.backend.attempts.length, 0);
});

for (const mode of ["offline", "response-lost", "invalid-json"]) {
  test(`${mode}: durable queue replays the exact key and payload after reload`, async () => {
    const page = await boot();
    page.backend.mode = mode;
    await page.fillNote({ observation: "Check the support net", cropName: "Dahlia", bedOrField: "Bed 4" });
    await page.get("field-note-form").fire("submit");
    const [pending] = page.pending();
    assert.ok(pending, "uncertain writes must remain queued");
    assert.equal(pending.body.operationKey, pending.id);
    assert.equal(pending.body.observedOn, farmDate);
    assert.equal(page.get("field-note-form").elements.observation.value, "");
    assert.deepEqual(JSON.parse(page.storage.get(draftKey)), {});
    assert.match(page.get("field-note-message").textContent, /waiting to sync/);
    page.backend.mode = "online";
    const reload = await boot({ storage: page.storage, backend: page.backend });
    assert.equal(reload.pending().length, 0);
    assert.equal(page.backend.receipts.size, 1);
    assert.deepEqual(page.backend.attempts.at(-1), pending.body);
    assert.equal(reload.get("field-notes-list").children.length, 1);
    assert.match(reload.get("sync-detail").textContent, /No field entries/);
  });
}

test("storage failure blocks dispatch and preserves the entered draft", async () => {
  const page = await boot({ denyQueueWrites: true });
  await page.fillNote({ observation: "Keep this observation" });
  await page.get("field-note-form").fire("submit");
  assert.equal(page.backend.attempts.length, 0);
  assert.equal(page.get("field-note-form").elements.observation.value, "Keep this observation");
  assert.equal(JSON.parse(page.storage.get(draftKey)).note.observation, "Keep this observation");
  assert.match(page.get("field-note-message").textContent, /restore browser storage/);
  assert.equal(page.get("field-note-form").submitButton.disabled, false);
});

test("validation rejection preserves editable input; successful retry renders note as text", async () => {
  const page = await boot();
  page.backend.mode = "reject";
  const observation = "<img src=x onerror=alert(1)> needs inspection";
  await page.fillNote({ observation });
  await page.get("field-note-form").fire("submit");
  assert.equal(page.pending().length, 0);
  assert.equal(page.get("field-note-form").elements.observation.value, observation);
  assert.ok(JSON.parse(page.storage.get(draftKey)).note);
  assert.equal(JSON.parse(page.storage.get(draftKey)).note._submission, undefined);
  page.backend.mode = "online";
  await page.get("field-note-form").fire("submit");
  assert.equal(page.backend.receipts.size, 1);
  assert.equal(page.get("field-notes-list").children[0].children[1].textContent, observation);
  assert.match(page.get("field-note-message").textContent, /Knowledge Log/);
});

test("failed queue replay remains visible and retryable without dropping a note", async () => {
  const page = await boot();
  page.backend.mode = "offline";
  await page.fillNote({ observation: "Water pooling near bed 7" });
  await page.get("field-note-form").fire("submit");
  const pending = page.pending();
  const reload = await boot({ storage: page.storage, backend: page.backend });
  assert.deepEqual(reload.pending(), pending);
  assert.match(reload.get("sync-detail").textContent, /1 entry is waiting/);
  assert.match(reload.get("pending-list").children[0].children[0].textContent, /Water pooling/);
  page.backend.mode = "online";
  await reload.get("retry-sync").fire("click");
  assert.equal(reload.pending().length, 0);
  assert.equal(page.backend.receipts.size, 1);
});

test("only the submitted capture form is locked until a delayed response settles", async () => {
  const page = await boot();
  await page.fillNote({ observation: "First observation" });
  let releaseResponse;
  page.backend.waitForResponse = new Promise(resolve => { releaseResponse = resolve; });
  const submission = page.get("field-note-form").fire("submit");
  assert.ok([...page.get("field-note-form").elements].every(control => control.disabled));
  assert.ok([...page.get("harvest-form").elements].every(control => !control.disabled));
  assert.equal(JSON.parse(page.storage.get(draftKey)).note.observation, "First observation");
  releaseResponse();
  await submission;
  assert.ok([...page.get("field-note-form").elements].every(control => !control.disabled));
  assert.equal(page.backend.receipts.size, 1);
  assert.equal(page.get("field-note-form").elements.observation.value, "");
});

test("reload while a save is in flight recovers the operation without restoring it as a new draft", async () => {
  const page = await boot();
  await page.fillNote({ observation: "Keep the original save key" });
  let releaseResponse;
  page.backend.waitForResponse = new Promise(resolve => { releaseResponse = resolve; });
  const submission = page.get("field-note-form").fire("submit");
  const crashStorage = new Map(page.storage);
  const original = page.pending()[0].body;
  releaseResponse();
  await submission;
  const reload = await boot({ storage: crashStorage, backend: page.backend });
  assert.equal(reload.pending().length, 0);
  assert.equal(page.backend.receipts.size, 1);
  assert.deepEqual(page.backend.attempts.at(-1), original);
  assert.equal(reload.get("field-note-form").elements.observation.value, "");
  assert.deepEqual(JSON.parse(crashStorage.get(draftKey)), {});
});

test("a draft cleanup failure recovers the previous receipt instead of duplicating its record", async () => {
  const page = await boot({ denyDraftClear: true });
  await page.fillNote({ observation: "Receipt cleanup recovery" });
  await page.get("field-note-form").fire("submit");
  assert.equal(page.backend.receipts.size, 1);
  assert.equal(page.pending().length, 0);
  assert.ok(JSON.parse(page.storage.get(draftKey)).note._submission);
  const reload = await boot({ storage: page.storage, backend: page.backend });
  assert.equal(page.backend.receipts.size, 1);
  assert.equal(reload.get("field-note-form").elements.observation.value, "");
  assert.equal(reload.pending().length, 0);
});
