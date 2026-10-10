"use strict";

const CACHE = "flowersforever-worker-v11";
const SHELL = [
  "/worker/",
  "/worker/worker.css",
  "/worker/worker.js",
  "/worker/manifest.webmanifest"
];

self.addEventListener("install", event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener("activate", event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(
    keys.filter(key => key !== CACHE).map(key => caches.delete(key))
  )));
  self.clients.claim();
});

self.addEventListener("fetch", event => {
  const url = new URL(event.request.url);
  if (event.request.method !== "GET" || url.origin !== self.location.origin
      || url.pathname.startsWith("/api/")) return;

  if (event.request.mode === "navigate" && url.pathname.startsWith("/worker")) {
    event.respondWith(fetch(event.request)
      .catch(() => caches.match("/worker/")));
    return;
  }

  if (url.pathname.startsWith("/worker/")) {
    event.respondWith(caches.match(event.request)
      .then(cached => cached || fetch(event.request)));
  }
});
