const SHELL = 'chanflow-shell-v5';
self.addEventListener('install', event => { event.waitUntil(caches.open(SHELL).then(cache => cache.addAll(['/','/app.js','/preload.js','/style.css']))); self.skipWaiting(); });
self.addEventListener('activate', event => { event.waitUntil((async()=>{for(const key of await caches.keys())if(key!==SHELL&&key.startsWith('chanflow-shell-'))await caches.delete(key);await self.clients.claim();})()); });
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/') || url.pathname.startsWith('/media/')) return;
  event.respondWith(fetch(event.request).catch(async () => {const cache=await caches.open(SHELL);return await cache.match(event.request)||await cache.match('/');}));
});
