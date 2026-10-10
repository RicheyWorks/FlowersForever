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
  focus() { this.focused = true; this.onFocus?.(this); }
  scrollIntoView() {}
  querySelector(selector) {
    if (this.selectorMap) {
      assert.ok(this.selectorMap[selector], `Missing template control ${selector}`);
      return this.selectorMap[selector];
    }
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
  let activeElement = null;
  const makeElement = attributes => {
    const element = new Element(attributes);
    element.onFocus = focused => { activeElement = focused; };
    return element;
  };
  for (const tag of html.matchAll(/<[\w-]+\b([^>]*)>/g)) {
    const attributes = attrs(tag[1]);
    if (attributes.id) elements.set(attributes.id, makeElement(attributes));
  }
  for (const form of html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form>/g)) {
    const element = elements.get(attrs(form[1]).id);
    if (!element) continue; // Forms inside repeated task/order templates have no static id.
    element.elements = {};
    element.submitButton = makeElement();
    Object.defineProperty(element.elements, Symbol.iterator, {
      value: function* () { yield* Object.values(this); yield element.submitButton; }
    });
    for (const control of form[2].matchAll(/<(input|textarea|select)\b([^>]*)(?:>([\s\S]*?)<\/\1>|>)/g)) {
      const attributes = attrs(control[2]);
      if (!attributes.name) continue;
      const field = elements.get(attributes.id) || makeElement(attributes);
      field.value = attributes.value || (control[1] === "select"
        ? attrs((control[3] || "").match(/<option\b([^>]*)>/)?.[1] || "").value || "" : "");
      field.initialValue = field.value;
      element.elements[attributes.name] = field;
    }
    element.reset = () => Object.values(element.elements).forEach(field => { field.value = field.initialValue; });
  }
  const closeButtons = [...html.matchAll(/<button\b([^>]*data-close-capture[^>]*)>/g)].map(match => {
    const element = makeElement(attrs(match[1]));
    element.dataset.closeCapture = element.attributes["data-close-capture"];
    return element;
  });
  function templateFragment(id) {
    const fragment = makeElement();
    const selectors = {
      "task-template": [".task-card", ".task-date", ".task-type", ".task-title",
        ".task-location", ".task-notes", ".complete-toggle", ".task-form", ".form-message"],
      "order-template": [".order-card", ".order-date", ".order-type", ".order-customer",
        ".order-contact", ".order-account-note", ".order-instructions", ".order-lines",
        ".handoff-toggle", ".handoff-form", ".handoff-confirmation-text",
        ".complete-handoff", ".discard-handoff", ".form-message"],
      "inventory-template": [".inventory-card", ".inventory-category", ".inventory-name",
        ".inventory-on-hand", ".inventory-quantity", ".inventory-form"]
    }[id];
    fragment.selectorMap = Object.fromEntries(selectors.map(selector => [selector, makeElement()]));
    const formSelector = { "task-template": ".task-form", "order-template": ".handoff-form",
      "inventory-template": ".inventory-form" }[id];
    const form = fragment.querySelector(formSelector);
    const fieldNames = { "task-template": ["actualMinutes", "inputCost", "fieldObservation", "photo", "photoCaption"],
      "order-template": ["confirmedPhysicalHandoff"],
      "inventory-template": ["countedQuantity"] }[id];
    form.elements = Object.fromEntries(fieldNames.map(name => [name, makeElement()]));
    form.selectorMap = { "button[type=submit]": makeElement(), "input": form.elements[fieldNames[0]] };
    if (id === "inventory-template") {
      form.selectorMap[".discard-count"] = makeElement();
      form.selectorMap[".form-message"] = makeElement();
    }
    return fragment;
  }
  for (const id of ["task-template", "order-template", "inventory-template"]) {
    elements.get(id).content = { cloneNode: () => templateFragment(id) };
  }
  const document = {
    get activeElement() { return activeElement; },
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
    createElement() { return makeElement(); }
  };
  return { document, elements, closeButtons };
}

function server() {
  return { mode: "online", receipts: new Map(), attempts: [], writeRequests: [],
    csrfRequests: [], csrfToken: null, nextKey: 1 };
}

async function boot({ storage = new Map(), backend = server(), attachmentFiles = new Map(),
  denyQueueWrites = false, denyDraftClear = false } = {}) {
  const dom = buildDocument();
  const navigator = { onLine: true };
  const fetch = async (url, options = {}) => {
    if (options.method === "POST") {
      const body = options.body instanceof FormData ? Object.fromEntries(options.body) : JSON.parse(options.body);
      backend.attempts.push(body);
      backend.writeRequests.push({ url, options });
      if (options.credentials !== "same-origin"
          || !backend.csrfToken || options.headers?.["X-XSRF-TOKEN"] !== backend.csrfToken) {
        return response(403, { error: "CSRF token missing or invalid." });
      }
      if (backend.waitForResponse) await backend.waitForResponse;
      if (backend.mode === "offline") throw new TypeError("Connection unavailable");
      if (backend.mode === "reject") return response(400, { error: "Observation is required." });
      if (backend.mode === "csrf-reject") return response(403, { error: "CSRF token rejected." });
      const replayed = backend.receipts.has(body.operationKey);
      if (!replayed) backend.receipts.set(body.operationKey, { ...body, id: backend.receipts.size + 1, summary: body.observation });
      if (backend.mode === "response-lost") throw new TypeError("Response interrupted after commit");
      if (backend.mode === "invalid-json") return { ok: true, json: async () => { throw new SyntaxError("Truncated JSON"); } };
      if (url.includes("/tasks/") && backend.today) {
        backend.today.tasks = backend.today.tasks.filter(task => !url.includes(`/tasks/${task.id}/`));
      }
      if (url.includes("/orders/") && backend.orders) {
        backend.orders.orders = backend.orders.orders.filter(order => !url.includes(`/orders/${order.id}/`));
      }
      return response(replayed ? 200 : 201, { replayed, result: backend.receipts.get(body.operationKey) });
    }
    if (backend.mode === "offline") throw new TypeError("Connection unavailable");
    if (url === "/api/auth/csrf") {
      backend.csrfRequests.push(options);
      if (backend.mode === "csrf-unavailable") return response(503, { error: "Unavailable" });
      backend.csrfToken = `masked-token-${backend.csrfRequests.length}`;
      return response(200, { headerName: "X-XSRF-TOKEN", token: backend.csrfToken });
    }
    if (url.startsWith("/api/worker/today")) return response(200,
      backend.today || { date: farmDate, tasks: [], openTaskTotal: 0 });
    if (url.startsWith("/api/worker/orders")) return response(200,
      backend.orders || { orders: [], dueTotal: 0 });
    if (url.startsWith("/api/worker/inventory")) return response(200,
      backend.inventory || { items: [], matchingTotal: 0 });
    if (url.startsWith("/api/worker/field-notes")) return response(200, { notes: [...backend.receipts.values()] });
    throw new Error(`Unexpected request ${url}`);
  };
  const response = (status, body) => ({ ok: status < 400, status, json: async () => body });
  class FormData {
    constructor(form) { this.values = new Map(form
      ? Object.entries(form.elements).map(([name, field]) => [name, field.value]) : []); }
    append(name, value) { this.values.set(name, value); }
    get(name) { return this.values.get(name); }
    [Symbol.iterator]() { return this.values[Symbol.iterator](); }
  }
  class File {}
  const indexedDB = {
    open() {
      const request = { result: {
        close() {},
        transaction() {
          const transaction = {
            objectStore() {
              return {
                get(id) {
                  const read = { result: attachmentFiles.get(id) };
                  Promise.resolve().then(() => read.onsuccess?.());
                  return read;
                },
                delete(id) {
                  attachmentFiles.delete(id);
                  Promise.resolve().then(() => transaction.oncomplete?.());
                }
              };
            }
          };
          return transaction;
        }
      } };
      Promise.resolve().then(() => request.onsuccess?.());
      return request;
    }
  };
  vm.runInNewContext(script, {
    document: dom.document, navigator, window: new Element(), FormData, File, fetch, TypeError, indexedDB,
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

test("each save obtains a fresh token without caching or persisting it", async () => {
  const page = await boot();
  for (const observation of ["First note", "Second note"]) {
    await page.fillNote({ observation });
    await page.get("field-note-form").fire("submit");
  }
  assert.equal(page.backend.receipts.size, 2);
  assert.equal(page.backend.csrfRequests.length, 2);
  assert.ok(page.backend.csrfRequests.every(request => request.cache === "no-store"
    && request.credentials === "same-origin"));
  assert.deepEqual(page.backend.writeRequests.map(request => request.options.headers["X-XSRF-TOKEN"]),
    ["masked-token-1", "masked-token-2"]);
  assert.ok([...page.storage.values()].every(value => !value.includes("masked-token")
    && !value.includes("X-XSRF-TOKEN")), "tokens must not enter saved drafts or operations");
});

test("failed token handshake dispatches no write and replay obtains a new token", async () => {
  const page = await boot();
  page.backend.mode = "csrf-unavailable";
  await page.fillNote({ observation: "Keep until authorized" });
  await page.get("field-note-form").fire("submit");
  const [pending] = page.pending();
  assert.ok(pending);
  assert.equal(page.backend.attempts.length, 0);
  assert.equal(page.backend.receipts.size, 0);
  assert.ok(!JSON.stringify(pending).includes("token"));
  page.backend.mode = "online";
  await page.get("retry-sync").fire("click");
  assert.equal(page.pending().length, 0);
  assert.deepEqual(page.backend.attempts, [pending.body]);
  assert.equal(page.backend.writeRequests[0].options.headers["X-XSRF-TOKEN"], "masked-token-2");
});

test("queued multipart photo obtains a fresh token without setting a JSON content type", async () => {
  const blob = { bytes: "photo-bytes" };
  const item = { id: "photo-operation", kind: "attachment", targetType: "PRODUCTION_TASK",
    targetId: 12, capturedAt: "2026-09-05T12:00:00Z", fileName: "field.png",
    url: "/api/worker/attachments" };
  const files = new Map([[item.id, { blob, fileName: item.fileName }]]);
  const page = await boot({ storage: new Map([[queueKey, JSON.stringify([item])]]), attachmentFiles: files });
  assert.equal(page.pending().length, 0);
  assert.equal(page.backend.receipts.size, 1);
  assert.equal(page.backend.csrfRequests.length, 1);
  const request = page.backend.writeRequests[0];
  assert.equal(request.options.headers["X-XSRF-TOKEN"], "masked-token-1");
  assert.equal(request.options.headers["Content-Type"], undefined,
    "the browser must generate the multipart boundary");
  assert.equal(request.options.body.get("operationKey"), item.id);
  assert.equal(request.options.body.get("file"), blob);
  assert.equal(files.size, 0, "committed photo bytes are released from durable storage");
});

test("CSRF rejection does not automatically repeat a submitted write", async () => {
  const page = await boot();
  page.backend.mode = "csrf-reject";
  await page.fillNote({ observation: "Review the rejected save" });
  await page.get("field-note-form").fire("submit");
  assert.equal(page.backend.attempts.length, 1);
  assert.equal(page.backend.receipts.size, 0);
  assert.equal(page.pending().length, 0);
  assert.equal(page.get("field-note-form").elements.observation.value, "Review the rejected save");
  assert.match(page.get("field-note-message").textContent, /CSRF token rejected/);
  const reload = await boot({ storage: page.storage, backend: page.backend });
  assert.equal(page.backend.attempts.length, 1, "reload must not replay a forbidden write");
  assert.equal(reload.get("field-note-form").elements.observation.value, "Review the rejected save");
});

test("a forbidden queued replay waits for explicit review across reload", async () => {
  const page = await boot();
  page.backend.mode = "offline";
  await page.fillNote({ observation: "Review before another replay" });
  await page.get("field-note-form").fire("submit");
  const original = page.pending()[0].body;
  page.backend.mode = "csrf-reject";
  await page.get("retry-sync").fire("click");
  assert.equal(page.backend.attempts.length, 1);
  assert.equal(page.pending()[0].requiresReview, true);
  page.backend.mode = "online";
  const reload = await boot({ storage: page.storage, backend: page.backend });
  assert.equal(page.backend.attempts.length, 1, "startup must not replay a forbidden operation");
  assert.match(reload.get("sync-detail").textContent, /needs review/);
  await reload.get("retry-sync").fire("click");
  assert.equal(page.backend.attempts.length, 2);
  assert.deepEqual(page.backend.attempts[1], original, "explicit retry retains the original business key");
  assert.equal(reload.pending().length, 0);
  assert.equal(page.backend.receipts.size, 1);
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

test("retry shows sync progress only while a queued entry is in flight", async () => {
  const page = await boot();
  assert.equal(page.get("retry-sync").disabled, true);
  assert.equal(page.get("retry-sync").attributes["data-syncing"], "false");
  page.backend.mode = "offline";
  await page.fillNote({ observation: "Check stems after rain" });
  await page.get("field-note-form").fire("submit");
  page.backend.mode = "online";
  let releaseResponse;
  page.backend.waitForResponse = new Promise(resolve => { releaseResponse = resolve; });
  const syncing = page.get("retry-sync").fire("click");
  assert.equal(page.get("retry-sync").disabled, true);
  assert.equal(page.get("retry-sync").textContent, "Syncing…");
  assert.equal(page.get("retry-sync").attributes["data-syncing"], "true");
  assert.equal(page.get("retry-sync").attributes["aria-busy"], "true");
  assert.match(page.get("sync-detail").textContent, /Syncing 1 entry/);
  releaseResponse();
  await syncing;
  assert.equal(page.get("retry-sync").textContent, "Retry now");
  assert.equal(page.get("retry-sync").attributes["data-syncing"], "false");
  assert.equal(page.get("retry-sync").attributes["aria-busy"], "false");
  assert.match(page.get("sync-detail").textContent, /No field entries/);
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

test("completed task keeps confirmation and focus after its card disappears", async () => {
  const backend = server();
  backend.today = { date: farmDate, openTaskTotal: 1, tasks: [{
    id: 12, dueDate: farmDate, taskType: "HARVEST", cropName: "Dahlia", bedOrField: "Bed 4"
  }] };
  const page = await boot({ backend });
  const form = page.get("task-list").children[0].querySelector(".task-form");
  form.elements.actualMinutes.value = "20";
  form.elements.inputCost.value = "0";
  form.querySelector("button[type=submit]").focus();
  await form.fire("submit");
  assert.equal(page.get("task-list").children.length, 0);
  assert.match(page.get("task-action-status").textContent, /Task · Dahlia: Saved to FlowersForever/);
  assert.equal(page.get("task-action-status").hidden, false);
  assert.match(page.get("task-action-status").className, /success/);
  assert.equal(page.document.activeElement, page.get("task-action-status"));

  page.get("refresh-work").focus();
  await page.get("refresh-work").fire("click");
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(page.document.activeElement, page.get("refresh-work"),
    "ordinary refresh must not steal keyboard focus");
});

test("handoff and inventory results remain in their section after list refresh", async () => {
  const backend = server();
  backend.orders = { throughDate: farmDate, dueTotal: 1, orders: [{
    id: 8, orderDate: farmDate, handoffType: "PICKUP", customerName: "River Market",
    lines: [], reviewToken: "review-8"
  }] };
  backend.inventory = { matchingTotal: 1, items: [{
    id: 3, name: "Dahlia stems", category: "Flowers", quantity: 16, unit: "stems"
  }] };
  const page = await boot({ backend });
  const handoff = page.get("order-list").children[0].querySelector(".handoff-form");
  handoff.querySelector("button[type=submit]").focus();
  await handoff.fire("submit");
  assert.equal(page.get("order-list").children.length, 0);
  assert.match(page.get("order-action-status").textContent, /Order #8 · River Market: Saved to FlowersForever/);
  assert.equal(page.document.activeElement, page.get("order-action-status"));

  const count = page.get("inventory-list").children[0].querySelector(".inventory-form");
  count.elements.countedQuantity.value = "15";
  count.querySelector("button[type=submit]").focus();
  await count.fire("submit");
  assert.match(page.get("inventory-action-status").textContent, /Stock · Dahlia stems: Saved to FlowersForever/);
  assert.equal(page.document.activeElement, page.get("inventory-action-status"));
  assert.equal(page.get("inventory-list").children.length, 1);
});

test("queued count feedback remains visible when the inventory refresh fails offline", async () => {
  const backend = server();
  backend.inventory = { matchingTotal: 1, items: [{
    id: 4, name: "Rose stems", category: "Flowers", quantity: 10, unit: "stems"
  }] };
  const page = await boot({ backend });
  const count = page.get("inventory-list").children[0].querySelector(".inventory-form");
  count.elements.countedQuantity.value = "9";
  backend.mode = "offline";
  await count.fire("submit");
  assert.equal(page.pending().length, 1);
  assert.match(page.get("inventory-action-status").textContent,
    /Stock · Rose stems: Saved on this device and waiting to sync/);
  assert.match(page.get("inventory-action-status").className, /pending/);
  assert.equal(page.document.activeElement, page.get("inventory-action-status"));
});

test("save rejection persists as an error without moving focus from the editable task", async () => {
  const backend = server();
  backend.today = { date: farmDate, openTaskTotal: 1, tasks: [{
    id: 20, dueDate: farmDate, taskType: "HARVEST", cropName: "Rose"
  }] };
  const page = await boot({ backend, denyQueueWrites: true });
  const form = page.get("task-list").children[0].querySelector(".task-form");
  const save = form.querySelector("button[type=submit]");
  save.focus();
  await form.fire("submit");
  assert.match(page.get("task-action-status").textContent, /restore browser storage/);
  assert.match(page.get("task-action-status").className, /error/);
  assert.equal(page.document.activeElement, save);
  assert.equal(save.disabled, false);
  assert.equal(backend.attempts.length, 0);
});
