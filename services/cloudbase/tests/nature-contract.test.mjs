import test from 'node:test';
import assert from 'node:assert/strict';
import { handle, validateInput, validText } from '../wildflower-ai/protocol.mjs';

const env = { DEEPSEEK_API_KEY: 'unit-provider-placeholder', NATURE_PROXY_CLIENT_TOKEN: 'unit-client-placeholder-0000000000000000' };
const species = { scientificName: 'Ginkgo biloba', chineseName: '银杏', style: 'quiet' };
const general = { mode: 'general', scientificName: '', chineseName: '', style: 'quiet' };
const poem = '把目光交给一段安静，\n让每次停留都有新的意义。';
const journal = '试着留意每次停留的意义，\n再想想如何记录尚待理解的疑问。';
const request = (body, options = {}) => new Request('https://example.org/v1/nature', {
  method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${env.NATURE_PROXY_CLIENT_TOKEN}`, ...options.headers },
  body: options.raw ?? JSON.stringify(body),
});

test('nature mode allowlist keeps binomials mandatory for legacy and explicit species requests', () => {
  for (const style of ['quiet', 'journal']) {
    for (const body of [{ ...species, style }, { ...species, style, mode: 'species' }, { ...general, style }, { ...species, style, chineseName: '' }])
      assert.equal(validateInput(body), true, JSON.stringify(body));
  }
  for (const mode of [undefined, 'species']) {
    for (const scientificName of ['', 'Ginkgo', 'ginkgo biloba', 'Ginkgo Biloba', 'Ginkgo biloba extra', 'Ginkgo  biloba', ' Ginkgo biloba', '银杏', `G${'a'.repeat(98)} b`])
      assert.equal(validateInput({ ...species, ...(mode ? { mode } : {}), scientificName }), false);
  }
});

test('invalid modes, names, private fields and input sizes fail before the gate or provider', async () => {
  const invalid = [null, [], true, 'general', {},
    ...['', 'GENERAL', 'unknown', null, false, 1, {}].map(mode => ({ ...general, mode })),
    ...['quiet ', '', 'poetry', null].map(style => ({ ...general, style })),
    { ...general, scientificName: species.scientificName }, { ...general, chineseName: species.chineseName },
    { ...general, scientificName: ' ' }, { ...general, chineseName: ' ' },
    { mode: 'general', style: 'quiet' }, { ...general, scientificName: null }, { ...general, chineseName: null },
    { ...species, chineseName: '银'.repeat(13) }, { ...species, chineseName: 'private' },
  ];
  for (const body of [species, { ...species, mode: 'species' }, general, { ...general, style: 'journal' }]) {
    for (const key of ['nickname', 'note', 'photo', 'location', 'latitude', 'longitude', 'observedAt', 'installId', 'history', 'question', 'prompt', 'system', '__proto__'])
      invalid.push({ ...body, [key]: 'PRIVATE_SENTINEL' });
  }
  const dependencies = { authorize: async () => true, reserve: () => assert.fail('Invalid input reserved budget'), fetch: () => assert.fail('Invalid input reached provider') };
  for (const body of invalid) {
    assert.equal(validateInput(body), false, JSON.stringify(body));
    const response = await handle(request(body), env, dependencies);
    assert.equal(response.status, 400, JSON.stringify(body));
    assert.deepEqual(await response.json(), { error: 'input' });
  }
  for (const [raw, status] of [['{', 400], [`${JSON.stringify(general)}${' '.repeat(1024)}`, 413]])
    assert.equal((await handle(request(null, { raw }), env, dependencies)).status, status);
  assert.equal((await handle(request(general, { headers: { 'Content-Type': 'text/plain' } }), env, dependencies)).status, 415);
});

test('general prompts state unknown identity, prohibit invention and give distinct style instructions without private context', async t => {
  const durations = [];
  t.mock.method(AbortSignal, 'timeout', ms => { durations.push(ms); return new AbortController().signal; });
  const payloads = [];
  for (const style of ['quiet', 'journal']) {
    const usage = { prompt_tokens: 100, completion_tokens: 20 };
    const events = [];
    const response = await handle(request({ ...general, style }, { headers: { 'x-private-note': 'PRIVATE_SENTINEL' } }), env, {
      authorize: async () => true,
      reserve: async (_request, path) => { events.push(path); return { ok: true, id: 'reservation' }; },
      settle: async (id, actualUsage) => { assert.equal(id, 'reservation'); assert.deepEqual(actualUsage, usage); events.push('settle'); },
      fetch: async (url, options) => {
        events.push('fetch'); assert.equal(url, 'https://api.deepseek.com/chat/completions');
        assert.equal(options.method, 'POST'); assert.ok(options.signal instanceof AbortSignal);
        assert.deepEqual(options.headers, { Authorization: `Bearer ${env.DEEPSEEK_API_KEY}`, 'Content-Type': 'application/json' });
        const payload = JSON.parse(options.body); payloads.push(payload);
        return Response.json({ choices: [{ message: { content: style === 'quiet' ? poem : journal } }], usage });
      },
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { mode: 'general', scientificName: '', text: style === 'quiet' ? poem : journal, source: 'ai_original', version: 1 });
    assert.deepEqual(events, ['/v1/nature', 'fetch', 'settle']);
    assert.equal(response.headers.get('Cache-Control'), 'no-store');
  }
  assert.deepEqual(durations, [18000, 18000]);
  for (const payload of payloads) {
    assert.deepEqual(Object.keys(payload).sort(), ['max_tokens', 'messages', 'model', 'stream', 'thinking']);
    assert.equal(payload.max_tokens, 300); assert.equal(payload.model, 'deepseek-flash');
    assert.equal(payload.stream, false); assert.deepEqual(payload.thinking, { type: 'disabled' });
    assert.deepEqual(payload.messages.map(message => message.role), ['system', 'user']);
    for (const phrase of ['当前植物身份未知', '抽象观察邀请', '不得猜测、确认或编造任何物种', '植物名称、特征', '不得引用或编造诗词', '文献', '不陈述已经发生的经历'])
      assert.ok(payload.messages[0].content.includes(phrase), phrase);
    for (const value of ['PRIVATE_SENTINEL', env.NATURE_PROXY_CLIENT_TOKEN, env.DEEPSEEK_API_KEY, species.scientificName, species.chineseName, '主题植物：', '规范学名：'])
      assert.ok(!JSON.stringify(payload).includes(value), value);
  }
  assert.equal(payloads[0].messages[0].content, payloads[1].messages[0].content);
  assert.match(payloads[0].messages[1].content, /克制静谧的短诗.*舒缓留白与节奏/u);
  assert.match(payloads[1].messages[1].content, /自然手记的简短散文.*先留意、再思考如何记录/u);
  assert.notEqual(payloads[0].messages[1].content, payloads[1].messages[1].content);
  assert.ok(validText(poem)); assert.ok(validText(journal));
});

test('general mode preserves protocol authentication and fails closed on unavailable guards', async () => {
  const neverFetch = () => assert.fail('Guarded provider call');
  assert.equal((await handle(request(general, { headers: { Authorization: 'Bearer forged' } }), env, { permit: async () => true, fetch: neverFetch })).status, 401);
  assert.equal((await handle(request(general), env, { permit: async () => false, fetch: neverFetch })).status, 429);
  assert.equal((await handle(request(general), env, { permit: async () => { throw Error('PRIVATE_SENTINEL'); }, fetch: neverFetch })).status, 503);
  assert.equal((await handle(request(general), env, { fetch: neverFetch })).status, 503);
  const response = await handle(request(general), env, {
    reserve: async () => ({ ok: false, status: 429, code: 'rate_limited' }), fetch: neverFetch,
  });
  assert.equal(response.status, 429); assert.deepEqual(await response.json(), { error: 'rate_limited' });
});
