import test from 'node:test';
import assert from 'node:assert/strict';
import { documentValue, initializeLedger } from '../scripts/deployment-state.mjs';

test('both CloudBase document response shapes preserve an existing budget on repeated deployment', async () => {
  for (const array of [true, false]) {
    const existing = { _id: 'global-v1', schemaVersion: 1, totalMilli: 9999, dailyRequests: 23 };
    const database = { runTransaction: async fn => fn({ collection: () => ({ doc: () => ({
      get: async () => ({ data: array ? [existing] : existing }),
      set: async () => assert.fail('Existing budget must never be overwritten'),
    }) }) }) };
    assert.equal(documentValue({ data: array ? [existing] : existing }), existing);
    assert.equal(await initializeLedger(database, 'wildflower_ai_limits', { totalMilli: 0 }), false);
    assert.equal(existing.totalMilli, 9999);
  }
});

test('seed is created only when absent and never mutates the source seed', async () => {
  let value;
  const seed = { _id: 'global-v1', schemaVersion: 1, totalMilli: 0 };
  const database = { runTransaction: async fn => fn({ collection: () => ({ doc: () => ({
    get: async () => ({ data: value ? [value] : [] }), set: async next => { value = next; },
  }) }) }) };
  assert.equal(await initializeLedger(database, 'wildflower_ai_limits', seed), true);
  value.totalMilli = 65;
  assert.equal(await initializeLedger(database, 'wildflower_ai_limits', seed), false);
  assert.equal(value.totalMilli, 65); assert.equal(seed.totalMilli, 0); assert.equal(seed._id, 'global-v1');
});
