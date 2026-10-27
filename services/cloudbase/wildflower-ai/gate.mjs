import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

const integer = (env, name, fallback, min, max) => {
  const value = Number(env[name] ?? fallback);
  if (!Number.isSafeInteger(value) || value < min || value > max) throw new Error('invalid_limits');
  return value;
};
export function limits(env) {
  return {
    dailyRequests: integer(env, 'DAILY_REQUEST_LIMIT', 100, 1, 100),
    installDaily: integer(env, 'INSTALL_DAILY_LIMIT', 20, 1, 20),
    concurrent: integer(env, 'GLOBAL_CONCURRENT_LIMIT', 3, 1, 3),
    dailyBudget: integer(env, 'DAILY_BUDGET_MILLI_CNY', 1000, 1, 1000),
    totalBudget: integer(env, 'TOTAL_BUDGET_MILLI_CNY', 10000, 1, 10000),
    natureCharge: integer(env, 'NATURE_RESERVATION_MILLI_CNY', 5, 5, 1000),
    chatCharge: integer(env, 'CHAT_RESERVATION_MILLI_CNY', 20, 20, 1000),
    ttl: integer(env, 'SESSION_TTL_SECONDS', 21600, 300, 21600) * 1000,
    ipIssues: integer(env, 'SESSION_ISSUES_PER_IP_DAY', 8, 1, 8),
    installIssues: integer(env, 'SESSION_ISSUES_PER_INSTALL_DAY', 4, 1, 4),
    issues: integer(env, 'DAILY_SESSION_LIMIT', 128, 1, 128),
  };
}
export const initialState = () => ({ schemaVersion: 1, day: '', dailyRequests: 0, dailyMilli: 0, totalMilli: 0,
  sessionIssues: 0, issueInstalls: {}, issueIps: {}, installCalls: {}, leases: {} });
const failure = (status, code) => ({ ok: false, status, code });
const counter = value => Number.isSafeInteger(value) && value >= 0;
function validateState(state) {
  if (state?.schemaVersion !== 1 || typeof state.day !== 'string' ||
    !['dailyRequests', 'dailyMilli', 'totalMilli', 'sessionIssues'].every(k => counter(state[k]))) throw new Error('invalid_ledger');
  for (const key of ['issueInstalls', 'issueIps', 'installCalls', 'leases']) {
    const values = state[key];
    if (!values || typeof values !== 'object' || Array.isArray(values) || Object.keys(values).length > 256 ||
      Object.keys(values).some(k => !/^[0-9a-f]{32}$/.test(k))) throw new Error('invalid_ledger');
    if (!Object.values(values).every(counter)) throw new Error('invalid_ledger');
  }
}

/** Stateless signed installation sessions; the private signing key never leaves the function. */
export class Gate {
  constructor(env, store, clock = Date.now) {
    this.env = env; this.store = store; this.clock = clock; this.limits = limits(env);
    if (typeof env.SESSION_SIGNING_KEY !== 'string' || env.SESSION_SIGNING_KEY.length < 48 || env.SESSION_SIGNING_KEY.startsWith('sk-'))
      throw new Error('unconfigured');
  }
  hash(value) { return createHmac('sha256', this.env.SESSION_SIGNING_KEY).update(value).digest('hex').slice(0, 32); }
  sign(value) { return createHmac('sha256', this.env.SESSION_SIGNING_KEY).update(`session:${value}`).digest('base64url'); }
  authenticate(request) {
    const token = (request.headers.get('authorization') || '').match(/^Bearer (v1\.[0-9a-f]{32}\.[0-9]+\.[0-9a-f]{16}\.[A-Za-z0-9_-]{43})$/)?.[1];
    if (!token) return null;
    const parts = token.split('.'); const signed = parts.slice(0, 4).join('.');
    const a = Buffer.from(parts[4]), b = Buffer.from(this.sign(signed));
    const expiresAt = Number(parts[2]), now = this.clock();
    return a.length === b.length && timingSafeEqual(a, b) && Number.isSafeInteger(expiresAt) &&
      expiresAt > now && expiresAt <= now + this.limits.ttl + 60_000 ? { install: parts[1], expiresAt } : null;
  }
  async transact(action) {
    return this.store.transact(state => {
      validateState(state);
      const now = this.clock(), day = new Date(now).toISOString().slice(0, 10);
      if (state.day !== day) {
        state.day = day; state.dailyRequests = 0; state.dailyMilli = 0; state.sessionIssues = 0;
        state.issueInstalls = {}; state.issueIps = {}; state.installCalls = {};
      }
      // Expired leases release capacity, never refund a potentially billed call.
      for (const [id, expires] of Object.entries(state.leases)) if (expires <= now) delete state.leases[id];
      return action(state, now);
    });
  }
  async issue(installId, peerIp) {
    if (this.env.AI_ENABLED !== 'true') return failure(503, 'service_paused');
    const install = this.hash(`install:${installId}`), ip = this.hash(`ip:${peerIp}`), cap = this.limits;
    const nonce = randomBytes(8).toString('hex');
    return this.transact((state, now) => {
      if (state.sessionIssues >= cap.issues || (state.issueInstalls[install] || 0) >= cap.installIssues ||
        (state.issueIps[ip] || 0) >= cap.ipIssues) return failure(429, 'rate_limited');
      state.sessionIssues++; state.issueInstalls[install] = (state.issueInstalls[install] || 0) + 1;
      state.issueIps[ip] = (state.issueIps[ip] || 0) + 1;
      const expiresAt = now + cap.ttl, signed = `v1.${install}.${expiresAt}.${nonce}`;
      return { ok: true, token: `${signed}.${this.sign(signed)}`, expiresAt };
    });
  }
  async reserve(request, path) {
    const session = this.authenticate(request);
    if (!session) return failure(401, 'unauthorized');
    if (this.env.AI_ENABLED !== 'true') return failure(503, 'service_paused');
    const cap = this.limits, id = randomBytes(16).toString('hex');
    return this.transact((state, now) => {
      const charge = path === '/v1/chat' ? cap.chatCharge : cap.natureCharge;
      if (state.dailyRequests >= cap.dailyRequests || (state.installCalls[session.install] || 0) >= cap.installDaily ||
        Object.keys(state.leases).length >= cap.concurrent || state.dailyMilli + charge > cap.dailyBudget ||
        state.totalMilli + charge > cap.totalBudget) return failure(429, 'rate_limited');
      state.dailyRequests++; state.dailyMilli += charge; state.totalMilli += charge;
      state.installCalls[session.install] = (state.installCalls[session.install] || 0) + 1;
      state.leases[id] = now + 60_000;
      return { ok: true, id };
    });
  }
  async settle(id) { return this.transact(state => { delete state.leases[id]; return { ok: true }; }); }
}
