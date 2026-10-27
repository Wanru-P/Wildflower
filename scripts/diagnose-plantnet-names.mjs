// Explicit, bounded live diagnostic. Uploads only the existing licensed Ginkgo asset.
// Never prints/persists the authenticated URL or the API key.
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';

if (!process.argv.includes('--live')) throw new Error('Pass --live to make three metadata and two identification requests.');
const root = resolve(import.meta.dirname, '..');
const properties = await readFile(resolve(root, 'local.properties'), 'utf8');
const key = properties.match(/^PLANTNET_API_KEY\s*=\s*(.+)$/m)?.[1].trim();
if (!key) throw new Error('Pl@ntNet credential unavailable');
const folder = resolve(root, 'docs/wildflower/evidence/plantnet-names', new Date().toISOString().replace(/[:.]/g, '-'));
await mkdir(folder, { recursive: true });
const summary = [];
async function request(label, path, params, body) {
  const url = new URL(path, 'https://my-api.plantnet.org');
  for (const [name, value] of Object.entries(params)) url.searchParams.set(name, value);
  url.searchParams.set('api-key', key);
  try {
    const response = await fetch(url, { method: body ? 'POST' : 'GET', body, signal: AbortSignal.timeout(60000) });
    const raw = await response.text();
    if (raw.includes(key)) throw new Error('Response contains credential; not persisted');
    const json = JSON.parse(raw);
    await writeFile(resolve(folder, `${label}.json`), raw, { flag: 'wx' });
    const item = { label, method: body ? 'POST' : 'GET', path, params, status: response.status,
      responseFile: `${label}.json`, language: json.language,
      data: Array.isArray(json) ? json : json.results?.map(({ score, species }) => ({ score, species })) ?? json };
    summary.push(item);
    console.log(JSON.stringify(item));
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return json;
  } catch (error) {
    // fetch exceptions can contain the private URL. Report only the exception type.
    console.error(`${label}: ${error.name}; no private request details logged`);
    throw new Error(`Diagnostic stopped at ${label}`);
  }
}
const languages = await request('languages', '/v2/languages', {});
if (!languages.includes('zh')) throw new Error('Chinese code not advertised by service');
for (const lang of ['en', 'zh']) {
  await request(`heptapleurum-species-${lang}`, '/v2/species', { lang, prefix: 'Heptapleurum arboricola', page: '1', pageSize: '10' });
}
const image = await readFile(resolve(root, 'app/src/main/assets/explore/ginkgo.jpg'));
for (const lang of [null, 'zh']) {
  const form = new FormData();
  form.append('images', new Blob([image], { type: 'image/jpeg' }), 'ginkgo.jpg');
  form.append('organs', 'auto');
  await request(`ginkgo-identify-${lang ?? 'default'}`, '/v2/identify/all', {
    'nb-results': '5', 'include-related-images': 'true', ...(lang ? { lang } : {}),
  }, form);
}
await writeFile(resolve(folder, 'summary.json'), JSON.stringify({ capturedAt: new Date().toISOString(),
  image: { path: 'app/src/main/assets/explore/ginkgo.jpg', sha256: createHash('sha256').update(image).digest('hex'),
    role: 'Licensed editorial asset; not the user\'s Heptapleurum capture. No reproduction of its 40% score is claimed.' },
  requests: summary }, null, 2), { flag: 'wx' });
console.log(`Evidence: ${folder}`);
