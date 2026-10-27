import { handle } from './protocol.mjs';
import { Gate } from './gate.mjs';

export const json = (status, value) => Response.json(value, { status, headers: { 'Cache-Control': 'no-store' } });
const error = (status, code) => json(status, { error: code });
export function normalizePath(path, prefix = '/wildflower-ai') {
  if (!/^\/[a-zA-Z0-9_/-]*$/.test(path) || path.includes('//')) return null;
  const base = prefix.replace(/\/$/, '');
  if (base && (path === base || path.startsWith(`${base}/`))) path = path.slice(base.length) || '/';
  return path.length > 1 ? path.replace(/\/$/, '') : path;
}
async function sessionBody(request) {
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.length > 1024) throw new Error('oversize');
  return JSON.parse(new TextDecoder().decode(bytes));
}

export function createApp(env, dependencies = {}) {
  let gate = null;
  try { if (dependencies.store) gate = new Gate(env, dependencies.store, dependencies.clock); } catch { /* fail closed */ }
  const configured = () => gate && typeof env.DEEPSEEK_API_KEY === 'string' && env.DEEPSEEK_API_KEY.trim().length > 0;
  return async (incoming, context = {}) => {
    const original = new URL(incoming.url);
    const path = normalizePath(original.pathname, env.PUBLIC_BASE_PATH || '/wildflower-ai');
    if (!path || original.search) return error(404, 'not_found');
    if (incoming.method === 'GET' && ['/', '/health'].includes(path)) {
      const ready = Boolean(configured());
      return json(ready ? 200 : 503, { service: 'wildflower-ai', version: 1,
        status: !ready ? 'unconfigured' : env.AI_ENABLED === 'true' ? 'ready' : 'paused', model: env.DEEPSEEK_MODEL || 'deepseek-flash' });
    }
    if (incoming.method !== 'POST' || !['/v1/session', '/v1/chat', '/v1/nature'].includes(path)) return error(404, 'not_found');
    if (!configured()) return error(503, 'unconfigured');
    if (path === '/v1/session') {
      if (!(incoming.headers.get('content-type') || '').startsWith('application/json')) return error(415, 'content_type');
      let body;
      try { body = await sessionBody(incoming); } catch (e) { return error(e.message === 'oversize' ? 413 : 400, 'input'); }
      if (!body || Object.keys(body).length !== 1 || typeof body.installId !== 'string' ||
        !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.installId)) return error(400, 'input');
      try {
        const issued = await gate.issue(body.installId, context.peerIp || 'gateway');
        return issued.ok ? json(200, { token: issued.token, expiresAt: issued.expiresAt }) : error(issued.status, issued.code);
      } catch { return error(503, 'rate_guard_unavailable'); }
    }
    let timedOut = false;
    const request = new Request(`https://internal.invalid${path}`, incoming);
    const response = await handle(request, env, {
      authorize: async candidate => Boolean(gate.authenticate(candidate)),
      reserve: (candidate, route) => gate.reserve(candidate, route),
      settle: id => gate.settle(id),
      fetch: async (url, options) => {
        // Leave room for the two bounded ledger operations inside Android's 25s/20s read deadlines.
        const maximum = path === '/v1/chat' ? 19_000 : 14_000;
        const signal = AbortSignal.any([options.signal, AbortSignal.timeout(Math.min(dependencies.timeoutMs ?? maximum, maximum))]);
        try { return await (dependencies.fetch || fetch)(url, { ...options, signal, redirect: 'error' }); }
        catch (e) { timedOut = signal.aborted; throw e; }
      },
    });
    return timedOut ? error(504, 'provider_timeout') : response;
  };
}
