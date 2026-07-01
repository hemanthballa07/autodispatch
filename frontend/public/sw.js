/* AutoDispatch service worker v2
 * Strategies:
 *   navigation            → network-first, fallback /offline.html
 *   GET /api/v1/locations → stale-while-revalidate (DATA_CACHE)
 *   other GET /api/v1/*   → passthrough (no caching — auth risk)
 *   non-API GET           → cache-first (static assets)
 */
const CACHE_NAME = "autodispatch-shell-v2";
const DATA_CACHE = "autodispatch-data-v1";
const SHELL_URLS = [
  "/", "/book", "/history", "/profile",
  "/offline.html", "/icon-192.png", "/icon-512.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((c) => c.addAll(SHELL_URLS))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  const KEEP = new Set([CACHE_NAME, DATA_CACHE]);
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => !KEEP.has(k)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;
  const url = new URL(request.url);

  // 1. Navigation — network-first, fallback to cached page, then /offline.html
  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request).catch(() =>
        caches.match(request).then((c) => c ?? caches.match("/offline.html"))
      )
    );
    return;
  }

  // 2. Locations (unauthenticated) — stale-while-revalidate
  //    Only intercepted in production where NEXT_PUBLIC_API_BASE="" (same-origin).
  if (url.pathname === "/api/v1/locations") {
    event.respondWith(
      caches.open(DATA_CACHE).then(async (cache) => {
        const cached = await cache.match(request);
        const fresh = fetch(request).then((res) => {
          if (res.ok) cache.put(request, res.clone());
          return res;
        });
        if (cached) {
          fresh.catch(() => {}); // suppress unhandled rejection when offline + cache hit
          return cached;
        }
        return fresh; // first load — wait for network
      })
    );
    return;
  }

  // 3. Other API calls (carry Authorization header) — pass through, no caching
  if (url.pathname.startsWith("/api/")) return;

  // 4. Static assets — cache-first
  event.respondWith(
    caches.match(request).then((c) => c ?? fetch(request))
  );
});
