/* AutoDispatch offline shell.
 * Pre-caches the app shell and serves it when the network is unavailable.
 * No data caching yet — booking flows are undesigned.
 */
const CACHE_NAME = "autodispatch-shell-v1";
const SHELL_URLS = ["/", "/book", "/icon-192.png", "/icon-512.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_URLS))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  event.respondWith(
    fetch(event.request).catch(() =>
      caches
        .match(event.request)
        .then((cached) => cached ?? caches.match("/"))
    )
  );
});
