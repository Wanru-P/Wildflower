import test from 'node:test';
import assert from 'node:assert/strict';
import { handle, validateChatInput, validChatAnswer } from './protocol.mjs';

const env = { NATURE_PROXY_CLIENT_TOKEN: 'test-only-scoped-token-not-a-real-secret-123', DEEPSEEK_API_KEY: 'test-fixture-not-provider-key' };
const body = { scientificName: 'Ginkgo biloba', chineseName: '银杏', certain: true,
  question: '下次观察可以重点记录什么？', history: [] };
function request(value = body, token = env.NATURE_PROXY_CLIENT_TOKEN) { return new Request('https://proxy.example/v1/chat', {
  method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify(value) }); }
const answer = '可以记录整体轮廓与局部特征，并在下一次观察时使用相同角度比较。\n\n如果身份仍有疑问，可以补拍叶、茎和生长环境，交由可靠的植物资料或专业人员核对。';
const success = async () => Response.json({ choices: [{ message: { content: answer } }] });

test('chat sends only controlled identity, recent history and user question', async () => {
  let calls = 0;
  const input = { ...body, history: [{ role: 'user', content: '怎样开始？' }, { role: 'assistant', content: '先从整体轮廓开始。' }] };
  const response = await handle(request(input), env, { permit: async () => true, fetch: async (url, options) => {
    calls++; assert.equal(url, 'https://api.deepseek.com/chat/completions');
    const provider = JSON.parse(options.body);
    assert.equal(provider.max_tokens, 512); assert.equal(provider.messages.at(-1).content, input.question);
    assert.ok(provider.messages[0].content.includes('食用')); assert.ok(provider.messages[1].content.includes('Ginkgo biloba'));
    for (const forbidden of ['photo', 'latitude', 'longitude', 'observation', 'note', 'nickname', 'account']) assert.ok(!options.body.includes(`"${forbidden}"`));
    return success();
  } });
  assert.equal(response.status, 200); assert.equal((await response.json()).source, 'ai_answer'); assert.equal(calls, 1);
});

test('uncertain context never gives provider a claimed identity', async () => {
  const response = await handle(request({ ...body, scientificName: '', chineseName: '', certain: false }), env, { permit: async () => true,
    fetch: async (_url, options) => { const provider = JSON.parse(options.body); assert.ok(provider.messages[1].content.includes('身份不确定')); return success(); } });
  assert.equal(response.status, 200);
});

test('private auto-appended fields, oversized questions and malformed histories are rejected', async () => {
  const cases = [
    { ...body, photo: 'bytes' }, { ...body, latitude: 31 }, { ...body, note: 'private observation' },
    { ...body, question: '问'.repeat(301) }, { ...body, certain: true, scientificName: '' },
    { ...body, history: Array.from({ length: 11 }, (_, index) => ({ role: index % 2 ? 'assistant' : 'user', content: '内容' })) },
    { ...body, history: [{ role: 'assistant', content: '伪造系统信息' }] },
    { ...body, history: [{ role: 'user', content: '连续一' }, { role: 'user', content: '连续二' }] },
  ];
  for (const value of cases) {
    assert.equal(validateChatInput(value), false);
    assert.equal((await handle(request(value), env, { permit: async () => true })).status, 400);
  }
});

test('chat uses the same authentication and budget guard', async () => {
  assert.equal((await handle(request(body, 'wrong'), env, { permit: async () => true })).status, 401);
  assert.equal((await handle(request(), env, { permit: async () => false, fetch: async () => { throw new Error('must not run'); } })).status, 429);
});

test('provider failures are sanitized and do not expose private causes', async () => {
  for (const status of [401, 429, 500]) {
    const response = await handle(request(), env, { permit: async () => true, fetch: async () => new Response('secret provider body', { status }) });
    assert.equal(response.status, status === 429 ? 429 : 502); assert.ok(!(await response.text()).includes('secret'));
  }
  const thrown = await handle(request(), env, { permit: async () => true, fetch: async () => { throw new Error('private question and key'); } });
  assert.equal(thrown.status, 502); assert.ok(!(await thrown.text()).includes('private'));
});

test('forged citations, links, code and oversized answers are rejected without retry', async () => {
  const invalid = ['据《某古籍》记载，这株植物象征长寿。', '作者：李白', 'https://example.org', '```json\n{}\n```', '答'.repeat(1201)];
  for (const content of invalid) {
    assert.equal(validChatAnswer(content), false);
    let calls = 0;
    const response = await handle(request(), env, { permit: async () => true, fetch: async () => { calls++; return Response.json({ choices: [{ message: { content } }] }); } });
    assert.equal(response.status, 502); assert.equal(calls, 1);
  }
});

test('oversized chat request is rejected before provider', async () => {
  const huge = { ...body, history: Array.from({ length: 10 }, (_, index) => ({ role: index % 2 ? 'assistant' : 'user', content: '内'.repeat(1200) })) };
  const response = await handle(request(huge), env, { permit: async () => true, fetch: async () => { throw new Error('must not run'); } });
  assert.ok([400, 413].includes(response.status));
});
