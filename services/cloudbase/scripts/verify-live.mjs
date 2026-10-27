import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { validateChatInput, validChatAnswer, validText } from '../wildflower-ai/protocol.mjs';

/** Four paid upstream calls only when live=true; the report never includes sessions or generated text. */
export async function verifyService(baseUrl, { live = false, requireReady = false, transport = fetch } = {}) {
  const url = new URL(baseUrl);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || !/^\/[A-Za-z0-9_/-]*$/.test(url.pathname) || /\/v1\/(chat|nature|session)\/?$/.test(url.pathname))
    throw new Error('https_service_base_required');
  const base = baseUrl.replace(/\/$/, '');
  const report = { baseUrl: base, checkedAt: new Date().toISOString(), mode: live ? 'live_provider' : 'route_probe',
    passed: false, generationRequests: 0, checks: [] };
  const exchange = async (path, body, token, method = 'POST') => {
    const started = Date.now();
    const response = await transport(`${base}${path}`, { method, redirect: 'error', signal: AbortSignal.timeout(32_000),
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(method === 'GET' ? {} : { body: JSON.stringify(body) }) });
    const reader = response.body?.getReader();
    if (!reader) throw new Error('empty_response');
    const chunks = []; let length = 0;
    while (true) {
      const { value, done } = await reader.read(); if (done) break;
      length += value.byteLength;
      if (length > 32768) { await reader.cancel(); throw new Error('oversized_response'); }
      chunks.push(value);
    }
    const bytes = new Uint8Array(length); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    const json = JSON.parse(new TextDecoder().decode(bytes));
    return { status: response.status, json, durationMs: Date.now() - started };
  };
  const check = (name, success, metadata = {}) => {
    report.checks.push({ name, passed: Boolean(success), ...metadata });
    if (!success) throw new Error(name);
  };
  try {
    const health = await exchange('/health', null, null, 'GET');
    check('health_is_new_function_ready', health.status === 200 && health.json.service === 'wildflower-ai' &&
      (health.json.status === 'ready' || !live && !requireReady && health.json.status === 'paused'), { serviceStatus: health.json.status });
    const chat = { scientificName: 'Ginkgo biloba', chineseName: '银杏', certain: true, question: '请把这次观察计划命名为“慢慢看”，并建议第一次记录这株植物可以从哪些方面开始。', history: [] };
    const nature = { scientificName: 'Ginkgo biloba', chineseName: '银杏', style: 'quiet' };
    for (const [path, body] of [['/v1/chat', chat], ['/v1/nature', nature]]) {
      const bad = await exchange(path, body);
      check(`mapped_and_requires_session:${path}`, bad.status === 401 && bad.json.error === 'unauthorized');
    }
    const wrongRoute = await exchange('/v1/not-a-route', {});
    check('unknown_route_404', wrongRoute.status === 404);
    if (!live) { report.passed = true; return report; }
    const session = await exchange('/v1/session', { installId: randomUUID() });
    const token = session.json.token;
    check('installation_session', session.status === 200 && typeof token === 'string' && token.length >= 50 && token.length <= 256 &&
      !token.startsWith('sk-') && !/\s/.test(token) && Number.isSafeInteger(session.json.expiresAt) && session.json.expiresAt > Date.now() + 30_000);
    for (const [path, body] of [['/v1/chat', { ...chat, note: 'must-not-be-forwarded' }], ['/v1/nature', { ...nature, style: 'invalid' }]]) {
      const response = await exchange(path, body, token);
      check(`invalid_input_400:${path}`, response.status === 400 && response.json.error === 'input');
    }
    const oversized = await exchange('/v1/nature', { ...nature, ignored: 'x'.repeat(2000) }, token);
    check('oversize_413', oversized.status === 413);
    const history = [];
    for (const question of [chat.question, '请结合刚才的方法，建议下一次怎样比较。', '刚才我给这次观察计划取了什么名字？请先重述名字，再把建议整理成简短步骤。']) {
      const body = { ...chat, question, history: structuredClone(history) };
      check('local_payload_valid', validateChatInput(body));
      report.generationRequests++;
      const response = await exchange('/v1/chat', body, token);
      check(`chat_turn_${history.length / 2 + 1}`, response.status === 200 && response.json.source === 'ai_answer' &&
        response.json.version === 1 && validChatAnswer(response.json.answer), { durationMs: response.durationMs, status: response.status });
      if (history.length === 4) check('chat_context_recalled', response.json.answer.includes('慢慢看'));
      history.push({ role: 'user', content: question }, { role: 'assistant', content: response.json.answer });
    }
    report.generationRequests++;
    const response = await exchange('/v1/nature', nature, token);
    check('nature_generation', response.status === 200 && response.json.source === 'ai_original' && response.json.version === 1 &&
      response.json.scientificName === nature.scientificName && validText(response.json.text), { durationMs: response.durationMs, status: response.status });
    report.passed = true;
  } catch (e) {
    // Never print upstream error bodies, credentials, headers or arbitrary exception messages.
    report.failure = report.checks.at(-1)?.passed === false ? report.checks.at(-1).name : (e?.name === 'TimeoutError' ? 'network_timeout' : 'network_or_response');
  }
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const value = flag => process.argv[process.argv.indexOf(flag) + 1];
  const base = process.argv.includes('--base-url') ? value('--base-url') : process.env.CLOUDBASE_AI_BASE_URL;
  if (!base) { console.error('Supply --base-url https://YOUR_GATEWAY/wildflower-ai; no credential is required in this command.'); process.exitCode = 2; }
  else {
    try {
      const report = await verifyService(base, { live: process.argv.includes('--live'), requireReady: process.argv.includes('--require-ready') });
      const path = new URL('../verification/', import.meta.url); await mkdir(path, { recursive: true });
      await writeFile(new URL(`../verification/${report.mode}.json`, import.meta.url), `${JSON.stringify(report, null, 2)}\n`);
      console.log(JSON.stringify(report, null, 2)); process.exitCode = report.passed ? 0 : 1;
    } catch { console.error('Invalid HTTPS service base; use the gateway URL plus /wildflower-ai, not /v1/chat or /v1/nature.'); process.exitCode = 2; }
  }
}
