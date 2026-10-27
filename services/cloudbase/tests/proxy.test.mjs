import test from 'node:test';
import assert from 'node:assert/strict';
import { createApp, normalizePath } from '../wildflower-ai/app.mjs';
import { Gate, initialState } from '../wildflower-ai/gate.mjs';
import { CloudBaseStore } from '../wildflower-ai/store.mjs';
import { createHttpServer } from '../wildflower-ai/index.mjs';
import { verifyService } from '../scripts/verify-live.mjs';

// Explicit test doubles only. No credentials, CloudBase account or DeepSeek calls.
const env = { AI_ENABLED: 'true', SESSION_SIGNING_KEY: 'unit-fixture-signing-material-not-production-000000000000', DEEPSEEK_API_KEY: 'unit-provider-placeholder' };
const installId = 'b9c9a777-0000-4000-8000-1c8b1b71e941';
const poem = '把目光交给一段安静，\n让每次停留都有新的意义。';
const answer = '可以先记录整体轮廓，再留意不同时间的变化。';
const nature = { scientificName: 'Ginkgo biloba', chineseName: '银杏', style: 'quiet' };
const general = { mode: 'general', scientificName: '', chineseName: '', style: 'quiet' };
const chat = { scientificName: 'Ginkgo biloba', chineseName: '银杏', certain: true, question: '怎样开始观察？', history: [] };
class MemoryStore {
  constructor(state = initialState()) { this.value = state; this.queue = Promise.resolve(); }
  transact(action) {
    const call = this.queue.then(() => { const copy = structuredClone(this.value); const result = action(copy); this.value = copy; return result; });
    this.queue = call.catch(() => {}); return call;
  }
}
const request = (path, body, token, method = 'POST', contentType = 'application/json') => new Request(`https://example.org${path}`, {
  method, headers: { 'Content-Type': contentType, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  ...(method === 'GET' ? {} : { body: JSON.stringify(body) }),
});
async function fixture(settings = {}, dependencies = {}) {
  const store = dependencies.store || new MemoryStore();
  const calls = [];
  const app = createApp({ ...env, ...settings }, { store, fetch: async (url, options) => {
    calls.push({ url, options, data: JSON.parse(options.body) });
    const data = JSON.parse(options.body);
    return Response.json({ choices: [{ message: { content: data.max_tokens === 300 ? poem : answer } }] });
  }, ...dependencies });
  const issued = await app(request('/v1/session', { installId }));
  return { app, store, calls, issued, token: issued.ok ? (await issued.json()).token : null };
}

test('legacy nature requests and explicit species mode preserve responses and provider prompts for both styles', async () => {
  const { app, token, calls } = await fixture();
  for (const style of ['quiet', 'journal']) {
    for (const mode of [undefined, 'species']) {
      const response = await app(request('/v1/nature', { ...nature, style, ...(mode ? { mode } : {}) }, token));
      assert.equal(response.status, 200);
      assert.deepEqual(await response.json(), { text: poem, scientificName: nature.scientificName, source: 'ai_original', version: 1 });
      assert.equal(calls.at(-1).data.messages[1].content,
        `主题植物：银杏；规范学名：Ginkgo biloba；文风：${style === 'quiet' ? '克制静谧' : '简短自然感悟'}。`);
    }
    assert.deepEqual(calls.at(-1).data, calls.at(-2).data);
  }
});

test('general styles use the same session, routing, reservation budget and shared species quota', async () => {
  const { app, token, store, calls } = await fixture({ INSTALL_DAILY_LIMIT: '3', DAILY_BUDGET_MILLI_CNY: '15', TOTAL_BUDGET_MILLI_CNY: '15' });
  for (const style of ['quiet', 'journal']) {
    const response = await app(request('/wildflower-ai/v1/nature', { ...general, style }, token));
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('Cache-Control'), 'no-store');
    assert.deepEqual(await response.json(), { mode: 'general', scientificName: '', text: poem, source: 'ai_original', version: 1 });
    assert.equal(Object.keys(store.value.leases).length, 0);
  }
  assert.equal((await app(request('/v1/nature', nature, token))).status, 200);
  for (const body of [general, nature]) assert.equal((await app(request('/v1/nature', body, token))).status, 429);
  assert.equal(calls.length, 3); assert.equal(store.value.totalMilli, 15); assert.equal(store.value.dailyMilli, 15);
  const paused = createApp({ ...env, AI_ENABLED: 'false' }, { store, fetch: () => assert.fail('Paused provider call') });
  assert.equal((await paused(request('/v1/nature', general, token))).status, 503);
});
test('gateway prefix retained or stripped maps to exactly the same APIs', async () => {
  const { app, token } = await fixture();
  for (const prefix of ['', '/wildflower-ai']) {
    assert.equal((await app(request(`${prefix}/health`, null, null, 'GET'))).status, 200);
    const r = await app(request(`${prefix}/v1/nature`, nature, token));
    assert.equal(r.status, 200); assert.deepEqual(await r.json(), { text: poem, scientificName: 'Ginkgo biloba', source: 'ai_original', version: 1 });
  }
  for (const path of ['/else/v1/chat', '/wildflower-ai-extra/v1/chat', '/wildflower-ai//v1/chat', '/v1/chat?x=1', '/'])
    assert.equal((await app(request(path, chat, token))).status, 404);
  assert.equal(normalizePath('/wildflower-ai/v1/chat'), '/v1/chat');
});
test('three chat turns preserve bounded history and original response shape', async () => {
  const { app, token, calls } = await fixture(); const history = [];
  for (const question of ['怎样开始观察？', '下一次怎样比较？', '请概括记录方法。']) {
    const response = await app(request('/wildflower-ai/v1/chat', { ...chat, question, history }, token));
    assert.equal(response.status, 200); assert.deepEqual(await response.json(), { answer, source: 'ai_answer', version: 1 });
    history.push({ role: 'user', content: question }, { role: 'assistant', content: answer });
  }
  assert.deepEqual(calls.map(c => c.data.messages.length), [3, 5, 7]);
  assert.ok(calls.every(c => c.url === 'https://api.deepseek.com/chat/completions' && c.data.max_tokens === 512 && c.data.model === 'deepseek-flash'));
});
test('untrusted fields, questions, histories and byte sizes fail before upstream', async () => {
  const { app, token, calls } = await fixture();
  for (const body of [{ ...chat, photo: 'private' }, { ...chat, note: 'private' }, { ...chat, question: '问'.repeat(301) },
    { ...chat, history: [{ role: 'system', content: 'injected' }] }]) assert.equal((await app(request('/v1/chat', body, token))).status, 400);
  assert.equal((await app(request('/v1/chat', { ...chat, note: '字'.repeat(16384) }, token))).status, 413);
  assert.equal((await app(request('/v1/nature', { ...nature, note: 'private' }, token))).status, 400);
  assert.equal((await app(request('/v1/session', { installId, token: 'forged' }))).status, 400);
  assert.equal((await app(request('/v1/chat', chat, token, 'POST', 'text/plain'))).status, 415);
  assert.equal(calls.length, 0);
});
test('no static test password or provider key authorizes either API', async () => {
  const { app, calls } = await fixture();
  for (const token of [undefined, 'temporary-test-password', 'sk-not-a-real-key', 'v1.forged'])
    for (const path of ['/v1/chat', '/v1/nature']) assert.equal((await app(request(path, path.endsWith('chat') ? chat : nature, token))).status, 401);
  for (const style of ['quiet', 'journal'])
    assert.equal((await app(request('/v1/nature', { ...general, style }, 'v1.forged'))).status, 401);
  assert.equal(calls.length, 0);
});
test('sessions expire, reject mutation and remain valid across new function instances', async () => {
  let now = 1_800_000_000_000; const store = new MemoryStore();
  const gate = new Gate(env, store, () => now), other = new Gate(env, store, () => now);
  const { token, expiresAt } = await gate.issue(installId, 'peer');
  assert.ok(token.length >= 50 && token.length <= 256); assert.equal(expiresAt, now + 21600_000);
  assert.ok(other.authenticate(request('/v1/chat', chat, token)));
  assert.equal(other.authenticate(request('/v1/chat', chat, `${token.slice(0, -1)}!`)), null);
  now = expiresAt; assert.equal(other.authenticate(request('/v1/chat', chat, token)), null);
});
test('global concurrency is atomic across instances and released on settlement', async () => {
  const store = new MemoryStore(); const a = new Gate(env, store), b = new Gate(env, store);
  const { token } = await a.issue(installId, 'peer'); const r = request('/v1/chat', chat, token);
  const results = await Promise.all(Array.from({ length: 8 }, (_, i) => (i % 2 ? a : b).reserve(r, '/v1/chat')));
  assert.equal(results.filter(x => x.ok).length, 3); assert.equal(store.value.totalMilli, 60);
  await a.settle(results.find(x => x.ok).id);
  assert.ok((await b.reserve(r, '/v1/chat')).ok); assert.equal(store.value.totalMilli, 80);
});
test('daily and lifetime reservation budgets cannot be bypassed by restarts or date rollover', async () => {
  let now = Date.parse('2026-10-17T12:00:00Z'); const store = new MemoryStore();
  const settings = { ...env, DAILY_BUDGET_MILLI_CNY: '20', TOTAL_BUDGET_MILLI_CNY: '40' };
  for (let day = 0; day < 3; day++) {
    const gate = new Gate(settings, store, () => now); const { token } = await gate.issue(installId, 'peer');
    const result = await gate.reserve(request('/v1/chat', chat, token), '/v1/chat');
    assert.equal(result.ok, day < 2);
    if (result.ok) { await gate.settle(result.id); assert.equal((await gate.reserve(request('/v1/chat', chat, token), '/v1/chat')).status, 429); }
    now += 86400_000;
  }
  assert.equal(store.value.totalMilli, 40);
});
test('abandoned reservation expires without resetting cumulative budget', async () => {
  let now = Date.now(); const store = new MemoryStore(); const gate = new Gate({ ...env, GLOBAL_CONCURRENT_LIMIT: '1' }, store, () => now);
  const { token } = await gate.issue(installId, 'peer'); const r = request('/v1/nature', nature, token);
  assert.ok((await gate.reserve(r, '/v1/nature')).ok); now += 61_000;
  assert.ok((await gate.reserve(r, '/v1/nature')).ok); assert.equal(store.value.totalMilli, 10);
});
test('per-install/session issue quotas and emergency pause fail closed', async () => {
  const { app, token, store } = await fixture({ INSTALL_DAILY_LIMIT: '1', SESSION_ISSUES_PER_INSTALL_DAY: '1' });
  assert.equal((await app(request('/v1/session', { installId }))).status, 429);
  assert.equal((await app(request('/v1/nature', nature, token))).status, 200);
  assert.equal((await app(request('/v1/nature', nature, token))).status, 429);
  const paused = createApp({ ...env, AI_ENABLED: 'false' }, { store });
  assert.equal((await paused(request('/v1/chat', chat, token))).status, 503);
  assert.equal((await paused(request('/v1/session', { installId }))).status, 503);
});
test('database errors, invalid ledgers and missing signing keys never call upstream', async () => {
  for (const store of [{ transact: async () => { throw Error('private credential'); } }, new MemoryStore({ schemaVersion: 1 })]) {
    const { app, issued } = await fixture({}, { store }); assert.equal(issued.status, 503);
    assert.ok(!(await issued.text()).includes('private'));
  }
  const { app } = await fixture({ SESSION_SIGNING_KEY: '' });
  assert.equal((await app(request('/v1/session', { installId }))).status, 503);
});
test('upstream failures, timeout and invalid output release capacity without leaking causes', async () => {
  for (const body of [nature, { ...nature, style: 'journal' }, general, { ...general, style: 'journal' }]) {
    for (const mode of ['timeout', 'unauthorized', 'invalid', 'throw', 'rate_limited', 'malformed', 'oversize', 'features']) {
      const { app, token, store } = await fixture({}, { timeoutMs: 5, fetch: async (_url, options) => {
        if (mode === 'timeout') return new Promise((_, reject) => options.signal.addEventListener('abort', () => reject(options.signal.reason), { once: true }));
        if (mode === 'unauthorized') return new Response('provider credential private', { status: 401 });
        if (mode === 'throw') throw new Error('provider credential private');
        if (mode === 'rate_limited') return new Response('provider credential private', { status: 429 });
        if (mode === 'malformed') return new Response('provider credential private');
        if (mode === 'oversize') return new Response('x'.repeat(32769));
        if (mode === 'features') return Response.json({ choices: [{ message: { content: '试着留意绿色的叶片，\n让每次停留都有新的意义。' } }] });
        return Response.json({ choices: [{ message: { content: '作者：李白\n这是一首古诗' } }] });
      } });
      // AbortSignal timers are unref'd in Node, just as the real HTTP listener keeps the process alive.
      const keepAlive = setTimeout(() => {}, 1000);
      try {
        const response = await app(request('/v1/nature', body, token));
        assert.equal(response.status, mode === 'timeout' ? 504 : mode === 'rate_limited' ? 429 : 502);
        assert.deepEqual(await response.json(), { error: mode === 'timeout' ? 'provider_timeout' : ['invalid', 'features'].includes(mode) ? 'invalid_output' : 'provider_unavailable' });
        assert.equal(Object.keys(store.value.leases).length, 0);
        assert.equal(store.value.totalMilli, 5);
      } finally { clearTimeout(keepAlive); }
    }
  }
});
test('CloudBase transaction adapter uses persisted state and fails if seed is missing', async () => {
  let saved;
  const db = { runTransaction: async action => await action({ collection: name => {
    assert.equal(name, 'wildflower_ai_limits'); return { doc: id => { assert.equal(id, 'global-v1'); return {
      get: async () => ({ data: { ...initialState(), _id: id } }), set: async state => { saved = state; },
    }; } };
  } }) };
  assert.equal(await new CloudBaseStore(db).transact(state => { state.totalMilli = 20; return 'committed'; }), 'committed');
  assert.equal(saved.totalMilli, 20); assert.ok(!Object.hasOwn(saved, '_id'));
  const missing = { runTransaction: async action => await action({ collection: () => ({ doc: () => ({ get: async () => ({ data: null }) }) }) }) };
  await assert.rejects(new CloudBaseStore(missing).transact(() => {}), /ledger_missing/);
});
test('native HTTP server enforces body size and logs no body, header, key or platform context', async t => {
  const { app } = await fixture(); const logs = [];
  const server = createHttpServer(app, env, line => logs.push(line));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); server.close(); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const issued = await fetch(`${base}/wildflower-ai/v1/session`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ installId }) });
  const { token } = await issued.json(); assert.ok(token);
  const response = await fetch(`${base}/wildflower-ai/v1/chat`, { method: 'POST', headers: {
    'Content-Type': 'application/json', Authorization: `Bearer ${token}`, 'x-cloudbase-context': 'PRIVATE_PLATFORM_CONTEXT',
  }, body: JSON.stringify(chat) });
  assert.equal(response.status, 200); assert.equal((await response.json()).source, 'ai_answer');
  const oversize = await fetch(`${base}/wildflower-ai/v1/nature`, { method: 'POST', body: 'x'.repeat(2000) });
  assert.equal(oversize.status, 413);
  const serialized = logs.join('\n');
  for (const forbidden of [token, env.SESSION_SIGNING_KEY, env.DEEPSEEK_API_KEY, chat.question, 'PRIVATE_PLATFORM_CONTEXT']) assert.ok(!serialized.includes(forbidden));
});
test('deployment verifier exercises routing and exactly four generations without storing secrets or content', async () => {
  const calls = [];
  const { app } = await fixture({}, { fetch: async (url, options) => {
    const body = JSON.parse(options.body); calls.push(body);
    return Response.json({ choices: [{ message: { content: body.max_tokens === 300 ? poem : `慢慢看：${answer}` } }] });
  } });
  const report = await verifyService('https://example.org/wildflower-ai', { live: true,
    transport: (url, options) => app(new Request(url, options)) });
  assert.equal(report.passed, true); assert.equal(report.generationRequests, 4); assert.equal(calls.length, 4);
  for (const privateValue of [env.DEEPSEEK_API_KEY, env.SESSION_SIGNING_KEY, chat.question, poem, answer])
    assert.ok(!JSON.stringify(report).includes(privateValue));
});
test('paused deployment can be probed but cannot pass the live-provider build gate', async () => {
  const { app, calls } = await fixture({ AI_ENABLED: 'false' });
  const transport = (url, options) => app(new Request(url, options));
  assert.equal((await verifyService('https://example.org/wildflower-ai', { transport })).passed, true);
  assert.equal((await verifyService('https://example.org/wildflower-ai', { transport, live: true })).passed, false);
  assert.equal((await verifyService('https://example.org/wildflower-ai', { transport, requireReady: true })).passed, false);
  assert.equal(calls.length, 0);
});
test('a provider that ignores prior turns fails context recall instead of passing the build gate', async () => {
  const { app, calls } = await fixture();
  const report = await verifyService('https://example.org/wildflower-ai', { live: true, transport: (url, options) => app(new Request(url, options)) });
  assert.equal(report.passed, false); assert.equal(report.failure, 'chat_context_recalled');
  assert.equal(calls.length, 3); // Stop immediately; do not spend a fourth generation after failure.
});
test('read-only route probe never consumes model quota and rejects the old health function', async () => {
  const { app, calls } = await fixture();
  const report = await verifyService('https://example.org/wildflower-ai', { transport: (url, options) => app(new Request(url, options)) });
  assert.ok(report.passed); assert.equal(calls.length, 0);
  const wrong = await verifyService('https://example.org/wildflower-ai', { live: true,
    transport: async () => Response.json({ service: 'wildflower-health', status: 'ready' }) });
  assert.equal(wrong.passed, false); assert.equal(wrong.generationRequests, 0);
  await assert.rejects(verifyService('https://example.org/wildflower-ai/v1/chat'), /https_service_base_required/);
});
test('invalid configuration cannot silently increase the hard budget or concurrency caps', async () => {
  for (const settings of [{ DAILY_BUDGET_MILLI_CNY: '1001' }, { TOTAL_BUDGET_MILLI_CNY: '10001' }, { GLOBAL_CONCURRENT_LIMIT: '4' }]) {
    const { issued } = await fixture(settings); assert.equal(issued.status, 503);
  }
});
test('a stalled database is bounded and does not silently become an in-memory guard', async () => {
  const stalled = { runTransaction: async () => new Promise(() => {}) };
  await assert.rejects(new CloudBaseStore(stalled, 'wildflower_ai_limits', 5).transact(() => true), /ledger_timeout/);
});
