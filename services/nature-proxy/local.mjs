// Loopback-only real provider integration harness. NOT a public production deployment.
import http from 'node:http';
import fs from 'node:fs';
import { randomBytes } from 'node:crypto';
import { handle } from './protocol.mjs';
const filename = new URL('./.dev.vars', import.meta.url);
const env = { ...process.env };
if (fs.existsSync(filename)) for (const line of fs.readFileSync(filename, 'utf8').split(/\r?\n/)) {
  const split = line.indexOf('='); if (split > 0 && !line.startsWith('#')) env[line.slice(0, split)] = line.slice(split + 1);
}
if (!env.DEEPSEEK_API_KEY) throw new Error('Server-only environment configuration required');
if (!env.NATURE_PROXY_CLIENT_TOKEN) {
  env.NATURE_PROXY_CLIENT_TOKEN = randomBytes(32).toString('hex');
  fs.writeFileSync(new URL('./.local-token', import.meta.url), env.NATURE_PROXY_CLIENT_TOKEN, { mode: 0o600 });
}
if (env.NATURE_PROXY_CLIENT_TOKEN.length < 32) throw new Error('Scoped proxy authentication required');
let minute = 0, calls = 0, day = 0, daily = 0;
const permit = async () => { const current = Math.floor(Date.now() / 60000), today = Math.floor(Date.now() / 86400000);
  if (minute !== current) { minute = current; calls = 0; } if (day !== today) { day = today; daily = 0; }
  if (calls >= 5 || daily >= 100) return false; calls++; daily++; return true; };
http.createServer(async (incoming, outgoing) => {
  try {
    let size = 0; const chunks = [];
    for await (const chunk of incoming) { size += chunk.length; if (size > 16384) { outgoing.writeHead(413); outgoing.end(); return; } chunks.push(chunk); }
    const request = new Request(`http://127.0.0.1:8787${incoming.url}`, { method: incoming.method, headers: incoming.headers,
      ...(['GET', 'HEAD'].includes(incoming.method) ? {} : { body: Buffer.concat(chunks) }) });
    const response = await handle(request, env, { permit });
    outgoing.writeHead(response.status, Object.fromEntries(response.headers)); outgoing.end(Buffer.from(await response.arrayBuffer()));
  } catch { outgoing.writeHead(500); outgoing.end(); }
}).listen(8787, '127.0.0.1', () => console.log('Local nature proxy listening on loopback:8787; authentication and budget enabled.'));
