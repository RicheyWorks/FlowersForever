"use strict";

const QUEUE_KEY = "flowersforever.worker.pending.v1";
const DRAFT_KEY = "flowersforever.worker.drafts.v1";
const ATTACHMENT_DATABASE = "flowersforever.worker.files.v1";
const ATTACHMENT_STORE = "pending-files";
const MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024;
const state = {
  flushing: false,
  queueReadable: true,
  serverReachable: null,
  syncError: "",
  farmToday: "",
  restoredDrafts: {}
};

const elements = {
  connectionDot: document.querySelector("#connection-dot"),
  connectionLabel: document.querySelector("#connection-label"),
  offlineBanner: document.querySelector("#offline-banner"),
  workDate: document.querySelector("#work-date"),
  harvestDate: document.querySelector("#harvest-date"),
  refreshWork: document.querySelector("#refresh-work"),
  taskList: document.querySelector("#task-list"),
  taskMessage: document.querySelector("#task-message"),
  taskActionStatus: document.querySelector("#task-action-status"),
  taskCount: document.querySelector("#task-count"),
  taskTemplate: document.querySelector("#task-template"),
  orderList: document.querySelector("#order-list"),
  orderMessage: document.querySelector("#order-message"),
  orderActionStatus: document.querySelector("#order-action-status"),
  orderCount: document.querySelector("#order-count"),
  orderTemplate: document.querySelector("#order-template"),
  inventorySearchForm: document.querySelector("#inventory-search-form"),
  inventorySearch: document.querySelector("#inventory-search"),
  inventoryList: document.querySelector("#inventory-list"),
  inventoryMessage: document.querySelector("#inventory-message"),
  inventoryActionStatus: document.querySelector("#inventory-action-status"),
  inventoryCount: document.querySelector("#inventory-count"),
  inventoryTemplate: document.querySelector("#inventory-template"),
  harvestForm: document.querySelector("#harvest-form"),
  harvestMessage: document.querySelector("#harvest-message"),
  harvestSection: document.querySelector("#harvest-section"),
  noteSection: document.querySelector("#field-note-section"),
  noteForm: document.querySelector("#field-note-form"),
  noteDate: document.querySelector("#field-note-date"),
  noteMessage: document.querySelector("#field-note-message"),
  noteList: document.querySelector("#field-notes-list"),
  notesMessage: document.querySelector("#field-notes-message"),
  cropOptions: document.querySelector("#farm-crop-options"),
  syncDetail: document.querySelector("#sync-detail"),
  pendingList: document.querySelector("#pending-list"),
  retrySync: document.querySelector("#retry-sync")
};

function captureForm(kind) {
  return kind === "note" ? elements.noteForm : elements.harvestForm;
}

function openCapture(kind) {
  elements.noteSection.hidden = kind !== "note";
  elements.harvestSection.hidden = kind !== "harvest";
  document.querySelector("#add-field-note").setAttribute("aria-expanded", String(kind === "note"));
  document.querySelector("#add-harvest").setAttribute("aria-expanded", String(kind === "harvest"));
  const section = kind === "note" ? elements.noteSection : elements.harvestSection;
  section.scrollIntoView({ block: "start" });
  captureForm(kind).elements[kind === "note" ? "observation" : "cropName"].focus({ preventScroll: true });
}

function storedDrafts() {
  const value = JSON.parse(localStorage.getItem(DRAFT_KEY) || "{}");
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Stored drafts could not be read.");
  }
  return value;
}

function rememberDraft(kind) {
  const message = kind === "note" ? elements.noteMessage : elements.harvestMessage;
  try {
    const drafts = storedDrafts();
    drafts[kind] = Object.fromEntries(new FormData(captureForm(kind)));
    localStorage.setItem(DRAFT_KEY, JSON.stringify(drafts));
    state.restoredDrafts[kind] = true;
    message.className = "form-message draft-saved";
    message.textContent = "Draft saved on this device. Choose Save when you are ready to share it.";
  } catch (_error) {
    message.className = "form-message error";
    message.textContent = "Your draft could not be stored. Keep this page open until it is saved.";
  }
}

function clearDraft(kind) {
  try {
    const drafts = storedDrafts();
    delete drafts[kind];
    localStorage.setItem(DRAFT_KEY, JSON.stringify(drafts));
    delete state.restoredDrafts[kind];
  } catch (_error) {
    // The submitted entry is already protected by its receipt or pending queue.
  }
}

function restoreDrafts() {
  try {
    const drafts = storedDrafts();
    for (const kind of ["note", "harvest"]) {
      if (!drafts[kind] || typeof drafts[kind] !== "object") continue;
      const submitted = drafts[kind]._submission;
      if (submitted) {
        // A tab can close after dispatch but before clearing the draft. Recover
        // its original operation, never offer that submission under a fresh key.
        const message = kind === "note" ? elements.noteMessage : elements.harvestMessage;
        if (enqueue(submitted)) {
          clearDraft(kind);
          message.textContent = "Your previously submitted entry is being recovered with its original save key.";
        } else {
          message.textContent = "Your submitted entry is stored in this draft. Restore browser storage and reload to recover it.";
        }
        continue;
      }
      const form = captureForm(kind);
      for (const [name, value] of Object.entries(drafts[kind])) {
        if (form.elements[name] && typeof value === "string") form.elements[name].value = value;
      }
      state.restoredDrafts[kind] = true;
      const message = kind === "note" ? elements.noteMessage : elements.harvestMessage;
      message.textContent = "Your unsaved draft is restored. Choose Save to share it with the farm.";
      document.querySelector(kind === "note" ? "#add-field-note small" : "#add-harvest small")
        .textContent = "Unsaved draft on this device";
    }
  } catch (_error) {
    elements.noteMessage.textContent = "A saved draft could not be read. Keep this site's data if you need to recover it.";
  }
}

function localDate() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

function operationKey() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;
}

function openAttachmentDatabase() {
  return new Promise((resolve, reject) => {
    if (!("indexedDB" in globalThis)) {
      reject(new Error("This browser does not provide durable photo storage."));
      return;
    }
    const request = indexedDB.open(ATTACHMENT_DATABASE, 1);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(ATTACHMENT_STORE)) {
        database.createObjectStore(ATTACHMENT_STORE, { keyPath: "id" });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error("Photo storage could not be opened."));
    request.onblocked = () => reject(new Error("Photo storage is open in an older field-work tab."));
  });
}

async function storeAttachmentFile(id, file) {
  const database = await openAttachmentDatabase();
  try {
    await new Promise((resolve, reject) => {
      const transaction = database.transaction(ATTACHMENT_STORE, "readwrite");
      transaction.objectStore(ATTACHMENT_STORE).put({
        id,
        blob: file,
        fileName: file.name || "field-photo",
        mediaType: file.type || "",
        lastModified: file.lastModified || Date.now()
      });
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error || new Error("Photo could not be stored."));
      transaction.onabort = () => reject(transaction.error || new Error("Photo storage was interrupted."));
    });
  } finally {
    database.close();
  }
}

async function loadAttachmentFile(id) {
  const database = await openAttachmentDatabase();
  try {
    return await new Promise((resolve, reject) => {
      const transaction = database.transaction(ATTACHMENT_STORE, "readonly");
      const request = transaction.objectStore(ATTACHMENT_STORE).get(id);
      request.onsuccess = () => resolve(request.result || null);
      request.onerror = () => reject(request.error || new Error("Photo could not be read."));
    });
  } finally {
    database.close();
  }
}

async function deleteAttachmentFile(id) {
  const database = await openAttachmentDatabase();
  try {
    await new Promise((resolve, reject) => {
      const transaction = database.transaction(ATTACHMENT_STORE, "readwrite");
      transaction.objectStore(ATTACHMENT_STORE).delete(id);
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error || new Error("Photo could not be removed."));
    });
  } finally {
    database.close();
  }
}

async function prepareAttachment(file, targetType, targetId, caption, targetLabel) {
  if (file.size < 1) return null;
  if (file.size > MAX_ATTACHMENT_BYTES) {
    throw new Error("The selected photo is larger than 15 MiB.");
  }
  const id = operationKey();
  await storeAttachmentFile(id, file);
  return {
    id,
    kind: "attachment",
    targetType,
    targetId,
    targetLabel,
    fileName: file.name || "field-photo",
    mediaType: file.type || "",
    byteSize: file.size,
    caption,
    capturedAt: new Date(file.lastModified || Date.now()).toISOString(),
    url: "/api/worker/attachments"
  };
}

function loadQueue() {
  try {
    const parsed = JSON.parse(localStorage.getItem(QUEUE_KEY) || "[]");
    if (!Array.isArray(parsed)) throw new Error("Pending queue is not an array.");
    state.queueReadable = true;
    return parsed;
  } catch (_error) {
    state.queueReadable = false;
    state.syncError = "The saved queue could not be read. Do not clear this site's data; ask an administrator to inspect it.";
    return [];
  }
}

function saveQueue(queue) {
  try {
    localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
    renderQueue();
    return true;
  } catch (_error) {
    state.syncError = "This browser could not store pending work. Keep this page open and restore storage access.";
    renderQueue();
    return false;
  }
}

function enqueue(item) {
  const queue = loadQueue();
  if (!state.queueReadable) return false;
  const existing = queue.findIndex(queued => queued.id === item.id);
  if (existing >= 0) queue[existing] = item; else queue.push(item);
  return saveQueue(queue);
}

function removeQueued(id) {
  const queue = loadQueue();
  if (!state.queueReadable) return false;
  const removed = queue.find(item => item.id === id);
  const saved = saveQueue(queue.filter(item => item.id !== id));
  if (saved && removed?.kind === "attachment") {
    deleteAttachmentFile(id).catch(() => {});
  }
  return saved;
}

function pendingLabel(item) {
  if (item.kind === "attachment") {
    return `Photo · ${item.fileName || "field image"} · ${item.targetLabel || `record #${item.targetId}`}`;
  }
  if (item.kind === "order-handoff") {
    return `Order #${item.orderId} · ${item.customerName || "customer handoff"}`;
  }
  if (item.kind === "inventory-count") {
    return `Inventory count · ${item.itemName || `item #${item.itemId}`}`;
  }
  if (item.kind === "task") return `Completed task #${item.taskId}`;
  if (item.kind === "harvest") return `Harvest · ${item.body?.cropName || "field entry"}`;
  if (item.kind === "field-note") return `Field note · ${item.body?.observation?.slice(0, 90) || "observation"}`;
  return "Pending field entry";
}

function renderQueue() {
  const queue = loadQueue();
  const count = queue.length;
  const waiting = count === 0
    ? "No field entries are waiting to sync."
    : `${count} ${count === 1 ? "entry is" : "entries are"} waiting on this device.`;
  elements.syncDetail.textContent = state.flushing
    ? count === 0 ? "Finishing sync…" : `Syncing ${count} ${count === 1 ? "entry" : "entries"} on this device…`
    : state.syncError ? `${waiting} Last sync issue: ${state.syncError}` : waiting;
  elements.pendingList.replaceChildren();
  queue.forEach(item => {
    const row = document.createElement("div");
    row.className = "pending-item";
    const label = document.createElement("span");
    label.textContent = pendingLabel(item);
    const discard = document.createElement("button");
    discard.type = "button";
    discard.className = "secondary";
    discard.textContent = "Discard from device";
    discard.addEventListener("click", () => {
      state.syncError = "";
      removeQueued(item.id);
      if (item.kind === "order-handoff") loadOrders();
      if (item.kind === "inventory-count") loadInventory();
      if (item.kind === "task") loadToday();
    });
    row.append(label, discard);
    elements.pendingList.append(row);
  });
  elements.retrySync.disabled = count === 0 || state.flushing || !state.queueReadable;
  elements.retrySync.textContent = state.flushing ? "Syncing…" : "Retry now";
  elements.retrySync.setAttribute("aria-busy", String(state.flushing));
  elements.retrySync.setAttribute("data-syncing", String(state.flushing));
}

function updateConnection() {
  const deviceOnline = navigator.onLine;
  const reachable = deviceOnline && state.serverReachable !== false;
  elements.connectionDot.classList.toggle("online", reachable);
  elements.connectionDot.classList.toggle("offline", !reachable);
  elements.connectionLabel.textContent = !deviceOnline
    ? "Offline"
    : state.serverReachable === false ? "Server unavailable" : "Connected";
  elements.offlineBanner.hidden = reachable;
  elements.offlineBanner.textContent = deviceOnline
    ? "FlowersForever cannot be reached. New entries stay on this device and are safe to retry."
    : "Offline. New entries stay on this device and retry when the connection returns.";
}

function markServerReachable(reachable) {
  state.serverReachable = reachable;
  updateConnection();
}

async function responseFailure(response) {
  try {
    const body = await response.json();
    return {
      message: body.error || body.message || `Request failed (${response.status})`,
      code: body.code || "",
      currentQuantity: body.currentQuantity
    };
  } catch (_error) {
    return { message: `Request failed (${response.status})`, code: "" };
  }
}

async function writeCsrfHeaders() {
  // Obtain a fresh masked token with its HttpOnly cookie immediately before
  // dispatch. Authentication tokens never become part of an offline operation.
  const response = await fetch("/api/auth/csrf", {
    credentials: "same-origin",
    cache: "no-store",
    headers: { "Accept": "application/json" }
  });
  if (!response.ok) throw new Error("The server could not authorize this save. Please retry.");
  const csrf = await response.json();
  if (csrf.headerName !== "X-XSRF-TOKEN" || typeof csrf.token !== "string" || !csrf.token.trim()) {
    throw new Error("The server returned an invalid save token. Please retry.");
  }
  return { [csrf.headerName]: csrf.token };
}

async function send(item) {
  let response;
  try {
    if (item.kind === "attachment") {
      const stored = await loadAttachmentFile(item.id);
      if (!stored?.blob) {
        const missing = new Error("The pending photo bytes are missing from this device.");
        missing.preserveQueued = true;
        throw missing;
      }
      const form = new FormData();
      form.append("operationKey", item.id);
      form.append("targetType", item.targetType);
      form.append("targetId", String(item.targetId));
      form.append("caption", item.caption || "");
      form.append("capturedAt", item.capturedAt);
      form.append("file", stored.blob, item.fileName || stored.fileName);
      const csrfHeaders = await writeCsrfHeaders();
      response = await fetch(item.url, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Accept": "application/json", ...csrfHeaders },
        body: form
      });
    } else {
      const csrfHeaders = await writeCsrfHeaders();
      response = await fetch(item.url, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", "Accept": "application/json", ...csrfHeaders },
        body: JSON.stringify(item.body)
      });
    }
  } catch (error) {
    if (!error.preserveQueued) {
      markServerReachable(false);
      error.retryable = true;
    }
    throw error;
  }
  markServerReachable(true);
  if (!response.ok) {
    const failure = await responseFailure(response);
    const error = new Error(failure.message);
    error.code = failure.code;
    error.status = response.status;
    error.currentQuantity = failure.currentQuantity;
    error.retryable = response.status >= 500
      || [401, 408, 425, 429].includes(response.status);
    throw error;
  }
  try {
    return await response.json();
  } catch (error) {
    // A successful write may have committed before its response was interrupted.
    error.retryable = true;
    throw error;
  }
}

function showActionResult(action, messageElement, focus = false) {
  if (!action) return;
  action.status.hidden = false;
  action.status.className = "inline-message action-status"
    + (messageElement.className.includes("error") ? " error"
      : messageElement.className.includes("success") ? " success" : " pending");
  action.status.textContent = `${action.subject}: ${messageElement.textContent}`;
  if (focus) action.status.focus();
}

async function submit(item, messageElement, action = null) {
  const queued = enqueue(item);
  if (!queued) {
    if (item.kind === "attachment") await deleteAttachmentFile(item.id).catch(() => {});
    messageElement.className = "form-message error";
    messageElement.textContent = item.kind === "attachment"
      ? "The photo could not be stored in the pending queue. Restore browser storage or remove the photo before completing the task."
      : "This entry could not be stored safely for retry. Keep the form open and restore browser storage before saving.";
    showActionResult(action, messageElement);
    return "rejected";
  }
  messageElement.className = "form-message";
  messageElement.textContent = "Saving…";
  showActionResult(action, messageElement);
  try {
    const response = await send(item);
    state.syncError = "";
    if (queued) removeQueued(item.id); else renderQueue();
    messageElement.className = "form-message success";
    messageElement.textContent = response.replayed
      ? "Already saved earlier—recovered without duplicating it."
      : item.kind === "order-handoff" && response.result?.alreadyFulfilled
        ? "This handoff was already completed without another stock deduction."
        : item.kind === "harvest" && response.result?.lotCode
          ? `Saved lot ${response.result.lotCode} to FlowersForever.`
          : "Saved to FlowersForever.";
    if (item.kind === "attachment") {
      renderQueue();
    } else if (item.kind === "order-handoff") {
      await Promise.all([loadOrders(), loadInventory()]);
    } else if (item.kind === "field-note") {
      messageElement.textContent = response.replayed
        ? "Field note already saved. Recovered without adding it twice."
        : "Field note saved to the farm's Knowledge Log.";
      await loadFieldNotes();
    } else if (item.kind === "inventory-count") {
      await loadInventory();
    } else {
      await loadToday();
    }
    showActionResult(action, messageElement, true);
    return "saved";
  } catch (error) {
    if (item.kind === "attachment" && error.preserveQueued && queued) {
      state.syncError = error.message;
      renderQueue();
      messageElement.className = "form-message error";
      messageElement.textContent = `${error.message} The queue entry remains for explicit discard.`;
      showActionResult(action, messageElement);
      return "conflict";
    }
    if (item.kind === "inventory-count"
        && error.code === "INVENTORY_COUNT_CONFLICT" && queued) {
      state.syncError = error.message;
      renderQueue();
      messageElement.className = "form-message error";
      messageElement.textContent = `${error.message} Your entered count is still saved on this device.`;
      await loadInventory();
      showActionResult(action, messageElement, true);
      return "conflict";
    }
    if (item.kind === "order-handoff"
        && (error.code === "ORDER_HANDOFF_CONFLICT" || !error.retryable) && queued) {
      state.syncError = error.message;
      renderQueue();
      messageElement.className = "form-message error";
      messageElement.textContent = `${error.message} The pending handoff remains on this device for review or discard.`;
      await loadOrders();
      showActionResult(action, messageElement, true);
      return "conflict";
    }
    if (!error.retryable && navigator.onLine) {
      if (queued) removeQueued(item.id);
      messageElement.className = "form-message error";
      messageElement.textContent = error.message;
      showActionResult(action, messageElement);
      return "rejected";
    }
    if (queued) {
      messageElement.className = "form-message";
      messageElement.textContent = "Saved on this device and waiting to sync. It is safe to retry.";
      if (item.kind === "inventory-count") await loadInventory();
      if (item.kind === "order-handoff") await loadOrders();
      showActionResult(action, messageElement,
        item.kind === "inventory-count" || item.kind === "order-handoff");
      return "queued";
    }
    messageElement.className = "form-message error";
    messageElement.textContent = "Could not save or store this entry. Keep the form open and restore the connection or browser storage.";
    showActionResult(action, messageElement);
    return "rejected";
  }
}

async function flushQueue({ reviewRejected = false } = {}) {
  if (state.flushing || !navigator.onLine) return;
  state.flushing = true;
  state.syncError = "";
  renderQueue();
  try {
    for (const item of loadQueue()) {
      if (item.requiresReview && !reviewRejected) {
        state.syncError = "A save was rejected and needs review. Use Retry now when you are ready to retry it.";
        break;
      }
      try {
        await send(item);
        removeQueued(item.id);
      } catch (error) {
        if (error.status === 403) {
          // A rejection is a decision, not an uncertain network outcome.
          // Persist the review requirement so reconnect/reload cannot replay it.
          item.requiresReview = true;
          enqueue(item);
        }
        state.syncError = error.message;
        break;
      }
    }
  } finally {
    state.flushing = false;
    renderQueue();
  }
  await Promise.all([loadToday(), loadOrders(), loadInventory(), loadFieldNotes()]);
}

function displayTask(task, today) {
  const fragment = elements.taskTemplate.content.cloneNode(true);
  const card = fragment.querySelector(".task-card");
  const date = fragment.querySelector(".task-date");
  const type = fragment.querySelector(".task-type");
  const title = fragment.querySelector(".task-title");
  const location = fragment.querySelector(".task-location");
  const notes = fragment.querySelector(".task-notes");
  const toggle = fragment.querySelector(".complete-toggle");
  const form = fragment.querySelector(".task-form");
  const message = fragment.querySelector(".form-message");

  date.textContent = task.dueDate < today ? `Overdue · ${task.dueDate}` : task.dueDate;
  date.classList.toggle("overdue", task.dueDate < today);
  type.textContent = String(task.taskType || "TASK").replaceAll("_", " ");
  title.textContent = task.cropName;
  location.textContent = task.bedOrField ? `📍 ${task.bedOrField}` : "No bed or field set";
  notes.textContent = task.notes || "";

  toggle.addEventListener("click", () => {
    const willOpen = form.hidden;
    form.hidden = !willOpen;
    toggle.setAttribute("aria-expanded", String(willOpen));
    toggle.textContent = willOpen ? "Close" : "Complete";
    if (willOpen) form.querySelector("input").focus();
  });

  form.addEventListener("submit", async event => {
    event.preventDefault();
    const data = new FormData(form);
    const submitButton = form.querySelector("button[type=submit]");
    submitButton.disabled = true;
    const photo = data.get("photo");
    if (photo instanceof File && photo.size > 0) {
      let attachment;
      try {
        attachment = await prepareAttachment(
          photo,
          "PRODUCTION_TASK",
          task.id,
          String(data.get("photoCaption") || ""),
          `task #${task.id} · ${task.cropName}`
        );
      } catch (error) {
        message.className = "form-message error";
        message.textContent = error.message || "The selected photo could not be stored on this device.";
        submitButton.disabled = false;
        return;
      }
      const photoOutcome = await submit(attachment, message);
      if (photoOutcome === "rejected" || photoOutcome === "conflict") {
        submitButton.disabled = false;
        return;
      }
      form.elements.photo.value = "";
      form.elements.photoCaption.value = "";
    }
    const id = operationKey();
    const item = {
      id,
      kind: "task",
      taskId: task.id,
      url: `/api/worker/tasks/${encodeURIComponent(task.id)}/complete`,
      body: {
        operationKey: id,
        actualMinutes: Number(data.get("actualMinutes")),
        inputCost: Number(data.get("inputCost")),
        fieldObservation: String(data.get("fieldObservation") || "")
      }
    };
    const outcome = await submit(item, message, {
      status: elements.taskActionStatus, subject: `Task · ${task.cropName}`
    });
    if (outcome === "rejected") submitButton.disabled = false;
  });

  const queuedTaskUrl = `/api/worker/tasks/${encodeURIComponent(task.id)}/complete`;
  const alreadyQueued = loadQueue().some(item => item.kind === "task"
    && (String(item.taskId) === String(task.id) || item.url === queuedTaskUrl));
  if (alreadyQueued) {
    toggle.disabled = true;
    toggle.textContent = "Waiting to sync";
  }

  card.dataset.taskId = String(task.id);
  elements.taskList.append(fragment);
}

function pendingOrderHandoff(orderId) {
  return loadQueue().find(item => item.kind === "order-handoff"
    && String(item.orderId) === String(orderId));
}

function displayQuantity(value) {
  return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function displayOrder(order, throughDate) {
  const fragment = elements.orderTemplate.content.cloneNode(true);
  const card = fragment.querySelector(".order-card");
  const date = fragment.querySelector(".order-date");
  const type = fragment.querySelector(".order-type");
  const customer = fragment.querySelector(".order-customer");
  const contact = fragment.querySelector(".order-contact");
  const accountNote = fragment.querySelector(".order-account-note");
  const instructions = fragment.querySelector(".order-instructions");
  const lines = fragment.querySelector(".order-lines");
  const toggle = fragment.querySelector(".handoff-toggle");
  const form = fragment.querySelector(".handoff-form");
  const confirmationText = fragment.querySelector(".handoff-confirmation-text");
  const submitButton = fragment.querySelector(".complete-handoff");
  const discardButton = fragment.querySelector(".discard-handoff");
  const message = fragment.querySelector(".form-message");
  const pending = pendingOrderHandoff(order.id);

  date.textContent = order.orderDate < throughDate
    ? `Overdue · ${order.orderDate}` : order.orderDate;
  date.classList.toggle("overdue", order.orderDate < throughDate);
  type.textContent = String(order.handoffType || "HANDOFF").replaceAll("_", " ");
  customer.textContent = `Order #${order.id} · ${order.customerName}`;
  const contactParts = [order.contactName, order.phone, order.email].filter(Boolean);
  contact.textContent = contactParts.length
    ? `Contact: ${contactParts.join(" · ")}` : "No customer contact saved";
  accountNote.textContent = order.customerNotes
    ? `Account note: ${order.customerNotes}` : "";
  instructions.textContent = order.instructions
    ? `Handoff instructions: ${order.instructions}` : "";
  order.lines.forEach(line => {
    const entry = document.createElement("li");
    entry.textContent = `${line.productName} · ${displayQuantity(line.quantity)} ${line.unit}`;
    lines.append(entry);
  });
  confirmationText.textContent = `I verified this ${String(order.handoffType).toLowerCase()} physically happened and the complete product load left the farm.`;

  function showForm(open) {
    form.hidden = !open;
    toggle.setAttribute("aria-expanded", String(open));
    toggle.textContent = open ? "Close review" : pending
      ? "Review pending handoff" : "Review handoff";
    if (open) form.querySelector("input").focus();
  }

  toggle.addEventListener("click", () => showForm(form.hidden));

  if (pending) {
    discardButton.hidden = false;
    submitButton.textContent = "Review & retry handoff";
    message.textContent = "This handoff is pending on this device. Review the current load before retrying.";
    showForm(true);
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    const currentPending = pendingOrderHandoff(order.id);
    const id = currentPending ? currentPending.id : operationKey();
    const queueItem = {
      id,
      kind: "order-handoff",
      orderId: order.id,
      customerName: order.customerName,
      url: `/api/worker/orders/${encodeURIComponent(order.id)}/complete`,
      body: { operationKey: id, reviewToken: order.reviewToken }
    };
    submitButton.disabled = true;
    discardButton.disabled = true;
    const outcome = await submit(queueItem, message, {
      status: elements.orderActionStatus,
      subject: `Order #${order.id} · ${order.customerName}`
    });
    if (outcome === "rejected" || outcome === "queued" || outcome === "conflict") {
      submitButton.disabled = false;
      discardButton.disabled = false;
      if (outcome !== "rejected") {
        submitButton.textContent = "Review & retry handoff";
        discardButton.hidden = false;
      }
    }
  });

  discardButton.addEventListener("click", () => {
    const queued = pendingOrderHandoff(order.id);
    state.syncError = "";
    if (queued) removeQueued(queued.id); else renderQueue();
    loadOrders();
  });

  card.dataset.orderId = String(order.id);
  elements.orderList.append(fragment);
}

async function loadOrders() {
  const through = elements.workDate.value || localDate();
  elements.orderMessage.hidden = false;
  elements.orderMessage.className = "inline-message empty";
  elements.orderMessage.textContent = "Loading handoffs…";
  try {
    const response = await fetch(
      `/api/worker/orders?through=${encodeURIComponent(through)}&limit=100`, {
        credentials: "same-origin",
        headers: { "Accept": "application/json" }
      });
    markServerReachable(true);
    if (!response.ok) {
      const failure = await responseFailure(response);
      throw new Error(failure.message);
    }
    const body = await response.json();
    elements.orderList.replaceChildren();
    elements.orderCount.textContent = String(body.dueTotal);
    if (body.orders.length === 0) {
      elements.orderMessage.textContent = "No confirmed handoffs are due through this work date.";
      return;
    }
    elements.orderMessage.hidden = !body.hasMore;
    if (body.hasMore) {
      elements.orderMessage.textContent = "Showing the first 100 due handoffs. Complete or reschedule work to reveal more.";
    }
    body.orders.forEach(order => displayOrder(order, body.throughDate));
  } catch (error) {
    if (error instanceof TypeError) markServerReachable(false);
    elements.orderMessage.hidden = false;
    elements.orderMessage.className = "inline-message";
    elements.orderMessage.textContent = navigator.onLine
      ? error.message : "Handoffs could not refresh while offline. Previously loaded orders remain visible.";
  }
}

function pendingInventoryCount(itemId) {
  return loadQueue().find(item => item.kind === "inventory-count"
    && String(item.itemId) === String(itemId));
}

function displayInventoryItem(item) {
  const fragment = elements.inventoryTemplate.content.cloneNode(true);
  const card = fragment.querySelector(".inventory-card");
  const category = fragment.querySelector(".inventory-category");
  const name = fragment.querySelector(".inventory-name");
  const onHand = fragment.querySelector(".inventory-on-hand");
  const quantity = fragment.querySelector(".inventory-quantity");
  const form = fragment.querySelector(".inventory-form");
  const input = form.elements.countedQuantity;
  const submitButton = form.querySelector("button[type=submit]");
  const discardButton = form.querySelector(".discard-count");
  const message = form.querySelector(".form-message");
  const pending = pendingInventoryCount(item.id);

  category.textContent = item.category || "Other";
  name.textContent = item.name;
  onHand.textContent = `System quantity reviewed: ${item.quantity} ${item.unit}`;
  quantity.textContent = `${item.quantity} ${item.unit}`;
  input.value = pending ? pending.body.countedQuantity : item.quantity;
  input.setAttribute("aria-label", `Physical count for ${item.name} in ${item.unit}`);

  if (pending) {
    discardButton.hidden = false;
    const reviewedUnit = pending.body.reviewedUnit || pending.unit || item.unit;
    message.textContent = `Pending count: ${pending.body.countedQuantity} ${reviewedUnit}. Review the current system quantity before retrying.`;
    submitButton.textContent = "Review & retry count";
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    const currentPending = pendingInventoryCount(item.id);
    const id = currentPending ? currentPending.id : operationKey();
    const queueItem = {
      id,
      kind: "inventory-count",
      itemId: item.id,
      itemName: item.name,
      unit: item.unit,
      url: `/api/worker/inventory/${encodeURIComponent(item.id)}/count`,
      body: {
        operationKey: id,
        reviewedQuantity: item.quantity,
        countedQuantity: Number(input.value),
        reviewedUnit: item.unit
      }
    };
    submitButton.disabled = true;
    discardButton.disabled = true;
    const outcome = await submit(queueItem, message, {
      status: elements.inventoryActionStatus, subject: `Stock · ${item.name}`
    });
    if (outcome === "rejected") {
      submitButton.disabled = false;
      discardButton.disabled = false;
    } else if (outcome === "queued" || outcome === "conflict") {
      submitButton.disabled = false;
      submitButton.textContent = "Review & retry count";
      discardButton.hidden = false;
      discardButton.disabled = false;
    }
  });

  discardButton.addEventListener("click", () => {
    const queued = pendingInventoryCount(item.id);
    state.syncError = "";
    if (queued) removeQueued(queued.id); else renderQueue();
    loadInventory();
  });

  card.dataset.inventoryId = String(item.id);
  elements.inventoryList.append(fragment);
}

async function loadInventory() {
  const query = elements.inventorySearch.value.trim();
  elements.inventoryMessage.hidden = false;
  elements.inventoryMessage.className = "inline-message empty";
  elements.inventoryMessage.textContent = "Loading inventory…";
  try {
    const response = await fetch(
      `/api/worker/inventory?query=${encodeURIComponent(query)}&limit=100`, {
        credentials: "same-origin",
        headers: { "Accept": "application/json" }
      });
    markServerReachable(true);
    if (!response.ok) {
      const failure = await responseFailure(response);
      throw new Error(failure.message);
    }
    const body = await response.json();
    elements.inventoryList.replaceChildren();
    if (!query) {
      state.inventoryProductNames = body.items.map(item => item.name);
      updateCropOptions();
    }
    elements.inventoryCount.textContent = String(body.matchingTotal);
    if (body.items.length === 0) {
      elements.inventoryMessage.textContent = query
        ? "No inventory matches this search."
        : "No inventory products are ready to count.";
      return;
    }
    elements.inventoryMessage.hidden = !body.hasMore;
    if (body.hasMore) {
      elements.inventoryMessage.textContent = "Showing the first 100 matches. Narrow the search to find another product.";
    }
    body.items.forEach(displayInventoryItem);
  } catch (error) {
    if (error instanceof TypeError) markServerReachable(false);
    elements.inventoryMessage.hidden = false;
    elements.inventoryMessage.className = "inline-message";
    elements.inventoryMessage.textContent = navigator.onLine
      ? error.message : "Inventory could not refresh while offline. Previously loaded counts remain visible.";
  }
}

function updateCropOptions() {
  const names = new Set([...(state.inventoryProductNames || []), ...(state.productExamples || [])]);
  elements.cropOptions.replaceChildren(...[...names].map(name => {
    const option = document.createElement("option");
    option.value = name;
    return option;
  }));
}

async function loadProductProfile() {
  try {
    const response = await fetch("/api/worker/product-profile", {
      credentials: "same-origin", headers: { "Accept": "application/json" }
    });
    if (!response.ok) return;
    const profile = await response.json();
    const unit = profile.units && profile.units[0];
    if (!unit) return;
    state.harvestDefaultUnit = unit;
    state.productExamples = profile.examples || [];
    if (!state.restoredDrafts.harvest && !state.harvestEdited) elements.harvestForm.elements.unit.value = unit;
    if (state.productExamples.length) {
      elements.harvestForm.elements.cropName.placeholder = "e.g. " + state.productExamples[0];
    }
    updateCropOptions();
  } catch (_error) {
    // Keep offline drafts and their explicit units available when the farm cannot be reached.
  }
}

async function loadToday(useFarmDate = false) {
  const date = elements.workDate.value || localDate();
  elements.taskMessage.hidden = false;
  elements.taskMessage.className = "inline-message empty";
  elements.taskMessage.textContent = "Loading work…";
  try {
    const response = await fetch(`/api/worker/today${useFarmDate ? "" : `?date=${encodeURIComponent(date)}`}`, {
      credentials: "same-origin",
      headers: { "Accept": "application/json" }
    });
    markServerReachable(true);
    if (!response.ok) {
      const failure = await responseFailure(response);
      throw new Error(failure.message);
    }
    const body = await response.json();
    if (useFarmDate) {
      state.farmToday = body.date;
      elements.workDate.value = body.date;
      if (!state.restoredDrafts.harvest) elements.harvestDate.value = body.date;
      if (!state.restoredDrafts.note) elements.noteDate.value = body.date;
      elements.noteForm.elements.followUpDate.min = elements.noteDate.value;
    }
    elements.taskList.replaceChildren();
    elements.taskCount.textContent = String(body.openTaskTotal);
    if (body.tasks.length === 0) {
      elements.taskMessage.textContent = "No overdue or due-today production work.";
      return;
    }
    elements.taskMessage.hidden = true;
    body.tasks.forEach(task => displayTask(task, body.date));
    if (body.hasMore) {
      elements.taskMessage.hidden = false;
      elements.taskMessage.textContent = "Showing the first 100 tasks. Finish or reschedule work to reveal more.";
    }
  } catch (error) {
    if (error instanceof TypeError) markServerReachable(false);
    elements.taskMessage.hidden = false;
    elements.taskMessage.className = "inline-message";
    elements.taskMessage.textContent = navigator.onLine
      ? error.message : "Work could not refresh while offline.";
  }
}

async function loadFieldNotes() {
  elements.notesMessage.hidden = false;
  elements.notesMessage.className = "inline-message empty";
  elements.notesMessage.textContent = "Loading notes…";
  try {
    const date = elements.workDate.value || localDate();
    const response = await fetch(`/api/worker/field-notes?through=${encodeURIComponent(date)}&limit=10`, {
      credentials: "same-origin", headers: { "Accept": "application/json" }
    });
    markServerReachable(true);
    if (!response.ok) throw new Error((await responseFailure(response)).message);
    const body = await response.json();
    elements.noteList.replaceChildren();
    elements.notesMessage.hidden = body.notes.length > 0;
    elements.notesMessage.textContent = "No field notes yet. Add a note when something is worth remembering.";
    for (const note of body.notes) {
      const card = document.createElement("article");
      card.className = "card field-note-card";
      card.dataset.noteId = String(note.id);
      const meta = document.createElement("p");
      meta.className = "field-note-meta";
      const kind = { PROBLEM: "Problem", OPPORTUNITY: "Idea", PRACTICE: "Observation" }[note.kind] || "Observation";
      meta.textContent = [note.observedOn, kind, note.cropName, note.bedOrField].filter(Boolean).join(" · ");
      const title = document.createElement("h3");
      title.textContent = note.summary;
      card.append(meta, title);
      if (note.observation !== note.summary) {
        const observation = document.createElement("p");
        observation.textContent = note.observation;
        card.append(observation);
      }
      if (note.actionTaken) {
        const action = document.createElement("p");
        action.textContent = `Action: ${note.actionTaken}`;
        card.append(action);
      }
      if (note.followUpDate || note.performedBy) {
        const followUp = document.createElement("p");
        followUp.className = "field-note-follow-up";
        followUp.textContent = [note.followUpDate ? `Follow up: ${note.followUpDate}` : "", note.performedBy]
          .filter(Boolean).join(" · ");
        card.append(followUp);
      }
      elements.noteList.append(card);
    }
  } catch (error) {
    if (error instanceof TypeError) markServerReachable(false);
    elements.notesMessage.className = "inline-message";
    elements.notesMessage.textContent = navigator.onLine
      ? error.message : "Notes could not refresh while offline. Previously loaded notes remain visible.";
  }
}

async function submitCapture(item, kind) {
  const form = captureForm(kind);
  try {
    const drafts = storedDrafts();
    drafts[kind] = { ...Object.fromEntries(new FormData(form)), _submission: item };
    localStorage.setItem(DRAFT_KEY, JSON.stringify(drafts));
  } catch (_error) {
    const message = kind === "note" ? elements.noteMessage : elements.harvestMessage;
    message.className = "form-message error";
    message.textContent = "This draft could not be prepared safely for retry. Keep the form open and restore browser storage before saving.";
    return "rejected";
  }
  const controls = Array.from(form.elements);
  const disabledBefore = controls.map(control => control.disabled);
  controls.forEach(control => { control.disabled = true; });
  let outcome;
  try {
    outcome = await submit(item, kind === "note" ? elements.noteMessage : elements.harvestMessage);
  } finally {
    controls.forEach((control, index) => { control.disabled = disabledBefore[index]; });
  }
  if (outcome === "rejected") {
    try {
      const drafts = storedDrafts();
      drafts[kind] = Object.fromEntries(new FormData(form));
      localStorage.setItem(DRAFT_KEY, JSON.stringify(drafts));
    } catch (_error) {
      // The original submission remains recoverable if storage has become unavailable.
    }
  }
  return outcome;
}

elements.noteForm.addEventListener("submit", async event => {
  event.preventDefault();
  const data = new FormData(elements.noteForm);
  const id = operationKey();
  const item = {
    id, kind: "field-note", url: "/api/worker/field-notes",
    body: {
      operationKey: id,
      observedOn: String(data.get("observedOn")),
      observation: String(data.get("observation") || ""),
      cropName: String(data.get("cropName") || ""),
      bedOrField: String(data.get("bedOrField") || ""),
      kind: String(data.get("kind") || "PRACTICE"),
      actionTaken: String(data.get("actionTaken") || ""),
      performedBy: String(data.get("performedBy") || ""),
      followUpDate: data.get("followUpDate") || null
    }
  };
  const outcome = await submitCapture(item, "note");
  if (outcome === "saved" || outcome === "queued") {
    clearDraft("note");
    elements.noteForm.reset();
    elements.noteDate.value = state.farmToday || localDate();
    elements.noteForm.elements.followUpDate.min = elements.noteDate.value;
    document.querySelector("#add-field-note small").textContent = "A problem, idea, or observation";
  }
});

elements.harvestForm.addEventListener("submit", async event => {
  event.preventDefault();
  const data = new FormData(elements.harvestForm);
  const id = operationKey();
  const item = {
    id,
    kind: "harvest",
    url: "/api/worker/harvests",
    body: {
      operationKey: id,
      harvestDate: String(data.get("harvestDate")),
      cropName: String(data.get("cropName")),
      quantity: Number(data.get("quantity")),
      unit: String(data.get("unit")),
      bedOrField: String(data.get("bedOrField") || ""),
      notes: String(data.get("notes") || ""),
      lotCode: String(data.get("lotCode") || ""),
      grade: String(data.get("grade") || "UNGRADED"),
      lossQuantity: Number(data.get("lossQuantity") || 0),
      storageLocation: String(data.get("storageLocation") || "")
    }
  };
  const outcome = await submitCapture(item, "harvest");
  if (outcome === "saved" || outcome === "queued") {
    clearDraft("harvest");
    elements.harvestForm.reset();
    elements.harvestDate.value = state.farmToday || localDate();
    elements.harvestForm.elements.unit.value = state.harvestDefaultUnit;
    elements.harvestForm.elements.grade.value = "UNGRADED";
    elements.harvestForm.elements.lossQuantity.value = "0";
    document.querySelector("#add-harvest small").textContent = "Cut flowers, stock, and lot details";
  }
});

elements.refreshWork.addEventListener("click", () => {
  loadToday();
  loadOrders();
  loadFieldNotes();
});
elements.workDate.addEventListener("change", () => {
  loadToday();
  loadOrders();
  loadFieldNotes();
});
elements.inventorySearchForm.addEventListener("submit", event => {
  event.preventDefault();
  loadInventory();
});
elements.retrySync.addEventListener("click", () => flushQueue({ reviewRejected: true }));
document.querySelector("#add-harvest").addEventListener("click", () => openCapture("harvest"));
document.querySelector("#add-field-note").addEventListener("click", () => openCapture("note"));
document.querySelector("#refresh-field-notes").addEventListener("click", loadFieldNotes);
document.querySelectorAll("[data-close-capture]").forEach(button => {
  button.addEventListener("click", () => {
    const kind = button.dataset.closeCapture;
    const opener = document.querySelector(kind === "note" ? "#add-field-note" : "#add-harvest");
    (kind === "note" ? elements.noteSection : elements.harvestSection).hidden = true;
    opener.setAttribute("aria-expanded", "false");
    opener.focus();
  });
});
elements.noteDate.addEventListener("change", () => {
  elements.noteForm.elements.followUpDate.min = elements.noteDate.value;
});
for (const kind of ["note", "harvest"]) {
  captureForm(kind).addEventListener("input", () => {
    if (kind === "harvest") state.harvestEdited = true;
    rememberDraft(kind);
  });
}
window.addEventListener("online", () => { updateConnection(); flushQueue(); });
window.addEventListener("offline", updateConnection);

elements.workDate.value = localDate();
elements.harvestDate.value = localDate();
elements.noteDate.value = localDate();
document.querySelector("#capture-workspace").append(elements.harvestSection, elements.noteSection);
restoreDrafts();
state.harvestDefaultUnit = elements.harvestForm.elements.unit.value;
elements.noteForm.elements.followUpDate.min = elements.noteDate.value;
updateConnection();
renderQueue();
loadToday(true).then(() => Promise.all([loadProductProfile(), loadOrders(), loadInventory(), loadFieldNotes()]))
  .then(flushQueue);

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/worker/sw.js").catch(() => {});
}
