import test from 'node:test';
import assert from 'node:assert/strict';
import { NatureRateGate } from './worker.mjs';

function fixture(env = {}) {
  const data = new Map();
  const storage = {
    get: async key => data.get(key),
    put: async (key, value) => data.set(key, structuredClone(value)),
    delete: async key => data.delete(key),
    transaction: async fn => fn(storage),
  };
  return { gate: new NatureRateGate({ storage }, { AI_ENABLED: 'true', ...env }), data };
}
function call(gate, path, body, headers = {}) {
  return gate.fetch(new Request(`https://gate${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) }));
}
async function session(gate, installId = 'f7c384ab-3905-48d4-9ca3-3d95b7820001') {
  const issued = await call(gate, '/session', { installId }, { 'X-Client-IP': '203.0.113.10' });
  assert.equal(issued.status, 200);
  return (await issued.json()).token;
}

test('opaque short-lived session authorizes a request and rejects provider-shaped keys', async () => {
  const { gate } = fixture();
  const token = await session(gate);
  assert.equal((await call(gate, '/authorize', {}, { Authorization: `Bearer ${token}` })).status, 200);
  assert.equal((await call(gate, '/authorize', {}, { Authorization: 'Bearer sk-not-a-client-credential' })).status, 401);
});

test('reserve is atomic: concurrency, per-install and budget caps fail before upstream work', async () => {
  const { gate } = fixture({ GLOBAL_CONCURRENT_LIMIT: '1', INSTALL_DAILY_LIMIT: '1', DAILY_BUDGET_MILLI_CNY: '20', CHAT_RESERVATION_MILLI_CNY: '20' });
  const token = await session(gate);
  const headers = { Authorization: `Bearer ${token}` };
  const first = await call(gate, '/reserve', { endpoint: '/v1/chat' }, headers);
  assert.equal(first.status, 200);
  assert.equal((await call(gate, '/reserve', { endpoint: '/v1/chat' }, headers)).status, 429);
  const firstId = (await first.json()).id;
  assert.equal((await call(gate, '/settle', { id: firstId, usage: { input: 20, output: 10 } })).status, 200);
  // Settling releases only concurrency; the conservative reservation remains charged.
  assert.equal((await call(gate, '/reserve', { endpoint: '/v1/chat' }, headers)).status, 429);
});

test('emergency switch remains closed even for a valid short-lived session', async () => {
  const { gate } = fixture({ AI_ENABLED: 'false' });
  const token = await session(gate);
  assert.equal((await call(gate, '/reserve', { endpoint: '/v1/nature' }, { Authorization: `Bearer ${token}` })).status, 503);
});
