import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { Readable } from 'node:stream';
import { fileURLToPath } from 'node:url';
import { validApiPath, validMediaPath } from './validation.mjs';

const root = fileURLToPath(new URL('./public/', import.meta.url));
const port = Number(process.env.PORT || 4173);
const cached = new Map();
let apiQueue = Promise.resolve(), lastApi = 0;
async function api(path) {
  const hit = cached.get(path);
  if (hit && Date.now() - hit.at < (path === 'boards.json' ? 3600000 : 30000)) return hit.body;
  const result = apiQueue.then(async () => {
    const secondHit = cached.get(path);
    if (secondHit && Date.now() - secondHit.at < 30000) return secondHit.body;
    await new Promise(resolve => setTimeout(resolve, Math.max(0, 1000 - (Date.now() - lastApi))));
    lastApi = Date.now();
    const response = await fetch(`https://a.4cdn.org/${path}`, {
      headers: {'User-Agent': 'ChanFlow/0.1.0 desktop-preview'}, redirect: 'error', signal: AbortSignal.timeout(25000)
    });
    if (!response.ok) throw Object.assign(new Error(response.status === 404 ? 'This thread has expired or was removed.' : `4chan returned ${response.status}. Please try again.`), {status: response.status});
    if (Number(response.headers.get('content-length')) > 8388608) throw new Error('Response is too large.');
    const body = await response.text();
    if (Buffer.byteLength(body) > 8388608) throw new Error('Response is too large.');
    if (cached.size >= 24) cached.delete(cached.keys().next().value);
    cached.set(path, {at: Date.now(), body});
    return body;
  });
  apiQueue = result.catch(() => {});
  return result;
}

const server = http.createServer(async (req, res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  try {
    const url = new URL(req.url, `http://127.0.0.1:${port}`);
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405).end(); return; }
    if (url.pathname.startsWith('/api/')) {
      const path = url.pathname.slice(5);
      if (!validApiPath(path)) { res.writeHead(400).end('Invalid API path'); return; }
      const body = await api(path);
      res.writeHead(200, {'Content-Type': 'application/json; charset=utf-8'}).end(body);
    } else if (url.pathname.startsWith('/media/')) {
      const path = url.pathname.slice(7);
      if (!validMediaPath(path)) { res.writeHead(400).end('Invalid media path'); return; }
      const abort = new AbortController();
      req.on('close', () => { if (!req.complete) abort.abort(); });
      res.on('close', () => { if (!res.writableEnded) abort.abort(); });
      const headers = {'User-Agent': 'ChanFlow/0.1.0 desktop-preview'};
      if (req.headers.range && /^bytes=\d*-\d*$/.test(req.headers.range)) headers.Range = req.headers.range;
      const upstream = await fetch(`https://i.4cdn.org/${path}`, {headers, redirect: 'error', signal: AbortSignal.any([abort.signal, AbortSignal.timeout(120000)])});
      const outgoing = {};
      for (const header of ['content-type','content-length','content-range','accept-ranges','cache-control']) {
        if (upstream.headers.has(header)) outgoing[header] = upstream.headers.get(header);
      }
      res.writeHead(upstream.status, outgoing);
      if (upstream.body && req.method !== 'HEAD') Readable.fromWeb(upstream.body).on('error', () => res.destroy()).pipe(res);
      else { await upstream.body?.cancel(); res.end(); }
    } else {
      const files = {'/':'index.html','/index.html':'index.html','/app.js':'app.js','/preload.js':'preload.js','/style.css':'style.css','/sw.js':'sw.js'};
      const filename = files[url.pathname];
      if (!filename) { res.writeHead(404).end('Not found'); return; }
      const body = await readFile(root + filename);
      const mime = filename.endsWith('.html') ? 'text/html' : filename.endsWith('.css') ? 'text/css' : 'text/javascript';
      res.writeHead(200, {'Content-Type': `${mime}; charset=utf-8`, 'Cache-Control': 'no-cache',
        'Content-Security-Policy': "default-src 'self'; img-src 'self' blob: data:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; worker-src 'self'; base-uri 'none'; frame-ancestors 'self'"}).end(body);
    }
  } catch (error) {
    if (res.headersSent) { res.destroy(); return; }
    res.writeHead(error.status || 502, {'Content-Type':'application/json'}).end(JSON.stringify({error:error.message || 'Could not connect to 4chan.'}));
  }
});
server.listen(port, '127.0.0.1', () => console.log(`ChanFlow desktop preview: http://127.0.0.1:${port}`));
