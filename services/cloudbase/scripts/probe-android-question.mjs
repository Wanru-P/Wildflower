import { randomUUID } from 'node:crypto';
const base = 'https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai';
// One explicit real call to isolate the Release UI's existing suggested question. No answer/token is logged.
async function post(path, body, token) {
  const start = Date.now();
  const response = await fetch(`${base}${path}`, { method: 'POST', redirect: 'error', signal: AbortSignal.timeout(30000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(body) });
  return { status: response.status, json: await response.json(), durationMs: Date.now() - start };
}
try {
  const session = await post('/v1/session', { installId: randomUUID() });
  if (session.status !== 200) throw new Error('session_failed');
  const result = await post('/v1/chat', { scientificName: 'Osmanthus fragrans', chineseName: '桂花', certain: true,
    question: '下次观察可以重点记录什么？', history: [] }, session.json.token);
  console.log(JSON.stringify({ status: result.status, error: result.json.error, source: result.json.source, durationMs: result.durationMs,
    answerLength: result.json.answer?.length }));
  process.exitCode = result.status === 200 ? 0 : 1;
} catch { console.log(JSON.stringify({ failure: 'network_or_session' })); process.exitCode = 1; }
