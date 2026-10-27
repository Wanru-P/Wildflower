import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { createApp, json, normalizePath } from './app.mjs';
import { CloudBaseStore } from './store.mjs';

/** Native HTTP server, not an event-function exports.main handler. */
export function createHttpServer(app, env = process.env, logger = console.log) {
  const server = createServer(async (incoming, outgoing) => {
    const started = Date.now(), requestId = randomUUID();
    let route = 'unknown', response;
    try {
      const url = new URL(incoming.url, 'http://internal.invalid');
      const normalized = normalizePath(url.pathname, env.PUBLIC_BASE_PATH || '/wildflower-ai');
      if (['/', '/health', '/v1/session', '/v1/chat', '/v1/nature'].includes(normalized)) route = normalized;
      const maximum = route === '/v1/chat' ? 16384 : 1024;
      const chunks = []; let length = 0;
      for await (const chunk of incoming) {
        length += chunk.length;
        if (length > maximum) { outgoing.writeHead(413, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', Connection: 'close' }); outgoing.end('{"error":"input"}'); incoming.resume(); return; }
        chunks.push(chunk);
      }
      const headers = new Headers();
      // CloudBase platform context, cookies and client-supplied forwarding metadata are not relayed.
      for (const name of ['authorization', 'content-type']) if (incoming.headers[name]) headers.set(name, incoming.headers[name]);
      const request = new Request(`https://internal.invalid${incoming.url}`, { method: incoming.method, headers,
        ...(['GET', 'HEAD'].includes(incoming.method) ? {} : { body: Buffer.concat(chunks) }) });
      response = await app(request, { peerIp: incoming.socket.remoteAddress || 'gateway' });
      outgoing.writeHead(response.status, { ...Object.fromEntries(response.headers), 'X-Request-Id': requestId });
      outgoing.end(Buffer.from(await response.arrayBuffer()));
    } catch {
      if (!outgoing.headersSent) outgoing.writeHead(503, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      outgoing.end('{"error":"unavailable"}');
    } finally {
      logger(JSON.stringify({ requestId, route, status: outgoing.statusCode, durationMs: Date.now() - started }));
    }
  });
  server.headersTimeout = 10_000; server.requestTimeout = 20_000; server.timeout = 50_000;
  return server;
}

export async function start(env = process.env) {
  let store;
  if (env.CLOUDBASE_ENV_ID && env.CLOUDBASE_APIKEY) {
    try {
      const { default: cloudbase } = await import('@cloudbase/node-sdk');
      // HTTP functions do not automatically receive event-function admin credentials.
      const client = cloudbase.init({ env: env.CLOUDBASE_ENV_ID, accessKey: env.CLOUDBASE_APIKEY, timeout: 4000 });
      store = new CloudBaseStore(client.database());
    } catch { /* HTTP health still starts and accurately reports unconfigured. */ }
  }
  const server = createHttpServer(createApp(env, { store }), env);
  server.listen(9000, '0.0.0.0', () => console.log('wildflower-ai listening on :9000'));
  return server;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await start();
