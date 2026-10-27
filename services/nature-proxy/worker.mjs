import { handle } from './protocol.mjs';

const encoder = new TextEncoder();
const json = (value, status = 200) => Response.json(value, { status, headers: { 'Cache-Control': 'no-store' } });
const number = (value, fallback, minimum = 0) => {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isSafeInteger(parsed) && parsed >= minimum ? parsed : fallback;
};
const utcDay = now => Math.floor(now / 86_400_000);
const randomToken = () => Array.from(crypto.getRandomValues(new Uint8Array(32)), byte => byte.toString(16).padStart(2, '0')).join('');
const sha256 = async value => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(value))), byte => byte.toString(16).padStart(2, '0')).join('');

function publicResponse(request, env) {
  const path = new URL(request.url).pathname;
  if (request.method !== 'GET' || !['/', '/health'].includes(path)) return null;
  return json({ service: 'wildflower-nature', status: env.AI_ENABLED === 'true' ? 'ready' : 'paused' });
}

function gateRequest(path, request, body = undefined) {
  const headers = new Headers({ 'Content-Type': 'application/json', 'X-Client-IP': request.headers.get('CF-Connecting-IP') || 'unknown' });
  const authorization = request.headers.get('Authorization');
  if (authorization) headers.set('Authorization', authorization);
  return new Request(`https://nature-gate${path}`, { method: 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body) });
}

async function gateJson(gate, path, request, body) {
  const response = await gate.fetch(gateRequest(path, request, body));
  const payload = await response.json().catch(() => ({}));
  return { ok: response.ok, status: response.status, code: payload.error, ...payload };
}

export default {
  async fetch(request, env) {
    const status = publicResponse(request, env);
    if (status) return status;
    if (!env.NATURE_GATE) return json({ error: 'unconfigured' }, 503);
    const gate = env.NATURE_GATE.get(env.NATURE_GATE.idFromName('global-budget'));
    const path = new URL(request.url).pathname;
    if (path === '/v1/session') {
      if (request.method !== 'POST' || !(request.headers.get('Content-Type') || '').startsWith('application/json')) return json({ error: 'not_found' }, 404);
      let body;
      try { body = await request.json(); } catch { return json({ error: 'input' }, 400); }
      if (!body || Object.keys(body).length !== 1 || typeof body.installId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.installId)) return json({ error: 'input' }, 400);
      const issued = await gateJson(gate, '/session', request, body);
      return json(issued.ok ? { token: issued.token, expiresAt: issued.expiresAt } : { error: issued.code || 'unavailable' }, issued.status);
    }
    return handle(request, env, {
      authorize: async candidate => (await gateJson(gate, '/authorize', candidate)).ok,
      reserve: async (candidate, endpoint) => gateJson(gate, '/reserve', candidate, { endpoint }),
      settle: async (id, usage) => { await gateJson(gate, '/settle', request, { id, usage: sanitizeUsage(usage) }); },
      fetch,
    });
  },
};

function sanitizeUsage(usage) {
  const input = Number.isSafeInteger(usage?.prompt_tokens) && usage.prompt_tokens >= 0 ? usage.prompt_tokens : null;
  const output = Number.isSafeInteger(usage?.completion_tokens) && usage.completion_tokens >= 0 ? usage.completion_tokens : null;
  return input === null || output === null ? null : { input, output };
}

/** One SQLite-backed object atomically guards opaque sessions, quotas and budget. It never stores prompt text, photos, notes, locations or provider secrets. */
export class NatureRateGate {
  constructor(state, env = {}) { this.state = state; this.env = env; }
  async fetch(request) {
    if (request.method !== 'POST') return json({ error: 'not_found' }, 404);
    const path = new URL(request.url).pathname;
    let body = {};
    try { body = await request.json(); } catch {
      // Authorization has no payload; all other routes validate their own body.
      if (path !== '/authorize') return json({ error: 'input' }, 400);
    }
    return this.state.storage.transaction(async storage => {
      const now = Date.now();
      if (path === '/session') return this.issue(storage, request, body, now);
      if (path === '/authorize') return this.authorize(storage, request, now);
      if (path === '/reserve') return this.reserve(storage, request, body, now);
      if (path === '/settle') return this.settle(storage, body, now);
      return json({ error: 'not_found' }, 404);
    });
  }
  limits() { return {
    enabled: this.env.AI_ENABLED === 'true', dailyRequests: number(this.env.DAILY_REQUEST_LIMIT, 100, 1),
    installDaily: number(this.env.INSTALL_DAILY_LIMIT, 20, 1), concurrent: number(this.env.GLOBAL_CONCURRENT_LIMIT, 3, 1),
    dailyBudget: number(this.env.DAILY_BUDGET_MILLI_CNY, 1000, 1), totalBudget: number(this.env.TOTAL_BUDGET_MILLI_CNY, 10000, 1),
    natureReservation: number(this.env.NATURE_RESERVATION_MILLI_CNY, 5, 1), chatReservation: number(this.env.CHAT_RESERVATION_MILLI_CNY, 20, 1),
    sessionTtl: number(this.env.SESSION_TTL_SECONDS, 21600, 300) * 1000, ipIssues: number(this.env.SESSION_ISSUES_PER_IP_DAY, 8, 1), installIssues: number(this.env.SESSION_ISSUES_PER_INSTALL_DAY, 4, 1),
  }; }
  async issue(storage, request, body, now) {
    if (typeof body?.installId !== 'string') return json({ error: 'input' }, 400);
    const day = utcDay(now), ip = await sha256(request.headers.get('X-Client-IP') || 'unknown'), install = await sha256(body.installId), limits = this.limits();
    const ipKey = `issue-ip:${day}:${ip}`, installKey = `issue-install:${day}:${install}`, ipCount = (await storage.get(ipKey)) || 0, installCount = (await storage.get(installKey)) || 0;
    if (ipCount >= limits.ipIssues || installCount >= limits.installIssues) return json({ error: 'rate_limited' }, 429);
    const token = randomToken(), tokenHash = await sha256(token), expiresAt = now + limits.sessionTtl;
    await storage.put(ipKey, ipCount + 1); await storage.put(installKey, installCount + 1); await storage.put(`session:${tokenHash}`, { install, expiresAt });
    return json({ token, expiresAt });
  }
  async session(storage, request, now) {
    const auth = request.headers.get('Authorization') || '';
    if (!auth.startsWith('Bearer ') || auth.length < 50) return null;
    const tokenHash = await sha256(auth.slice(7)), session = await storage.get(`session:${tokenHash}`);
    return session?.expiresAt > now ? { ...session, tokenHash } : null;
  }
  async authorize(storage, request, now) { return (await this.session(storage, request, now)) ? json({ ok: true }) : json({ error: 'unauthorized' }, 401); }
  async reserve(storage, request, body, now) {
    const session = await this.session(storage, request, now);
    if (!session) return json({ error: 'unauthorized' }, 401);
    const limits = this.limits();
    if (!limits.enabled) return json({ error: 'service_paused' }, 503);
    if (!body || !['/v1/nature', '/v1/chat'].includes(body.endpoint)) return json({ error: 'input' }, 400);
    const day = utcDay(now), state = (await storage.get('budget')) || { day, dailyRequests: 0, dailyMilli: 0, totalMilli: 0, concurrent: 0 };
    if (state.day !== day) { state.day = day; state.dailyRequests = 0; state.dailyMilli = 0; }
    const installKey = `install-use:${day}:${session.install}`, installCalls = (await storage.get(installKey)) || 0, amount = body.endpoint === '/v1/chat' ? limits.chatReservation : limits.natureReservation;
    if (state.dailyRequests >= limits.dailyRequests || installCalls >= limits.installDaily || state.concurrent >= limits.concurrent || state.dailyMilli + amount > limits.dailyBudget || state.totalMilli + amount > limits.totalBudget) return json({ error: 'rate_limited' }, 429);
    const id = randomToken(); state.dailyRequests++; state.dailyMilli += amount; state.totalMilli += amount; state.concurrent++;
    await storage.put('budget', state); await storage.put(installKey, installCalls + 1); await storage.put(`reservation:${id}`, { amount, endpoint: body.endpoint, createdAt: now });
    return json({ ok: true, id });
  }
  async settle(storage, body, now) {
    if (typeof body?.id !== 'string' || !/^[0-9a-f]{64}$/i.test(body.id)) return json({ error: 'input' }, 400);
    const reservationKey = `reservation:${body.id}`, reservation = await storage.get(reservationKey);
    if (!reservation) return json({ ok: true });
    const state = (await storage.get('budget')) || { day: utcDay(now), dailyRequests: 0, dailyMilli: 0, totalMilli: 0, concurrent: 0 };
    state.concurrent = Math.max(0, state.concurrent - 1);
    const usage = body.usage && Number.isSafeInteger(body.usage.input) && Number.isSafeInteger(body.usage.output) ? { input: body.usage.input, output: body.usage.output } : null;
    await storage.put('budget', state); await storage.put(`usage:${body.id}`, { endpoint: reservation.endpoint, amountMilli: reservation.amount, usage, settledAt: now }); await storage.delete(reservationKey);
    return json({ ok: true });
  }
}
