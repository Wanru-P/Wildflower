import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { createRequire } from 'node:module';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { randomBytes, createHash } from 'node:crypto';
import { join } from 'node:path';
import { documentValue, initializeLedger } from './deployment-state.mjs';

const run = promisify(execFile);
const require = createRequire(new URL('../tools/package.json', import.meta.url));
const ENV = 'wildflower-ai-test-d5cge7e1432c7';
const REGION = 'ap-shanghai';
const FUNCTION = 'wildflower-ai';
const COLLECTION = 'wildflower_ai_limits';
const DOMAIN = `${ENV}-1410768603.ap-shanghai.app.tcloudbase.com`;
const cli = process.env.CLOUDBASE_CLI_ENTRY || 'C:/Program Files/nodejs/node_global/node_modules/@cloudbase/cli/bin/tcb';
const out = value => console.log(JSON.stringify(value));
const privateCache = join(process.env.LOCALAPPDATA, 'Wildflower', 'CloudBase', 'wildflower-ai-server.dpapi');
async function protectedText(text, decrypt = false) {
  const script = decrypt
    ? 'Add-Type -AssemblyName System.Security; $b=[Convert]::FromBase64String([Console]::In.ReadToEnd()); [Console]::Out.Write([Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)))'
    : 'Add-Type -AssemblyName System.Security; $b=[Text.Encoding]::UTF8.GetBytes([Console]::In.ReadToEnd()); [Console]::Out.Write([Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)))';
  return new Promise((resolve, reject) => {
    const child = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let result = ''; child.stdout.on('data', chunk => { result += chunk; }); child.stderr.resume();
    child.on('error', () => reject(new Error('credential_protection_failed')));
    child.on('close', code => code === 0 ? resolve(result) : reject(new Error('credential_protection_failed')));
    child.stdin.end(text);
  });
}

// Capture all CLI output, including temporary login credentials, without logging it.
async function cliJson(args) {
  try {
    const { stdout } = await run(process.execPath, [cli, ...args, '-e', ENV, '-r', REGION, '--json'], { maxBuffer: 16 * 1024 * 1024, timeout: 120000 });
    const start = stdout.indexOf('{');
    if (start < 0) throw new Error('cli_non_json');
    const parsed = JSON.parse(stdout.slice(start));
    if (parsed.error) throw Object.assign(new Error('cloud_api_failed'), { code: parsed.error.code });
    return parsed.data ?? parsed;
  } catch (e) {
    // execFile errors contain the full command/output: never relay them.
    let code = e.code;
    for (const stream of [e.stdout, e.stderr]) {
      try { const start = stream?.indexOf('{'); if (start >= 0) code = JSON.parse(stream.slice(start)).error?.code || code; } catch {}
    }
    throw Object.assign(new Error('cli_failed'), { code: typeof code === 'string' && /^[A-Za-z0-9_.-]+$/.test(code) ? code : 'CLI_FAILURE' });
  }
}

async function main() {
  const temporary = await cliJson(['secrets', 'get']);
  if (!temporary.secretId || !temporary.secretKey || !temporary.token || !temporary.isTemporary) throw new Error('temporary_login_required');
  const Manager = require('@cloudbase/manager-node');
  const manager = new Manager({ envId: ENV, region: REGION, secretId: temporary.secretId, secretKey: temporary.secretKey, token: temporary.token, timeout: 60000 });
  const info = await manager.env.getEnvInfo();
  const env = info.EnvInfo ?? info;
  if (env.EnvId !== ENV || env.Functions?.[0]?.Region !== REGION || env.PackageId !== 'baas_trial') throw new Error('target_or_trial_mismatch');
  const scf = (Action, Param) => manager.commonService('scf', '2018-04-16').call({ Action, Param: { Namespace: ENV, ...Param } });
  const health = await scf('GetFunction', { FunctionName: 'wildflower-health' });
  const healthFingerprint = createHash('sha256').update(JSON.stringify({ code: health.CodeSha256, modified: health.ModTime, environment: health.Environment })).digest('hex');
  const variables = Object.fromEntries((health.Environment?.Variables || []).map(x => [x.Key, x.Value]));
  const tables = await manager.database.listCollections();
  const existingKeys = await manager.env.describeApiKeyList({ KeyType: 'api_key', PageSize: 100, PageNumber: 1 });
  out({ stage: 'read_only', env: env.EnvId, package: env.PackageId, database: env.Databases?.[0]?.Status,
    health: { name: health.FunctionName, status: health.Status, runtime: health.Runtime, hasDeepSeekKey: Boolean(variables.DEEPSEEK_API_KEY), hasCloudBaseKey: Boolean(variables.CLOUDBASE_APIKEY) },
    collections: (tables.Collections || []).map(x => ({ name: x.CollectionName, count: x.Count })), keyListFields: Object.keys(existingKeys),
    apiKeys: (existingKeys.Data || existingKeys.ApiKeyList || []).map(x => ({ name: x.Name, id: x.KeyId, expires: x.ExpireAt })) });
  if (process.argv.includes('--inspect-apk')) {
    const target = await scf('GetFunction', { FunctionName: FUNCTION });
    const values = Object.fromEntries((target.Environment?.Variables || []).filter(x => ['DEEPSEEK_API_KEY', 'CLOUDBASE_APIKEY', 'SESSION_SIGNING_KEY'].includes(x.Key)).map(x => [x.Key, x.Value]));
    if (Object.keys(values).length !== 3) throw new Error('secret_scan_configuration_missing');
    const script = new URL('./verify-apk.py', import.meta.url);
    const { fileURLToPath } = await import('node:url');
    const result = await new Promise((resolve, reject) => {
      const child = spawn('python', [fileURLToPath(script), fileURLToPath(new URL('../../../app/build/outputs/apk/release/app-release.apk', import.meta.url))], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
      let json = ''; child.stdout.on('data', chunk => { json += chunk; }); child.stderr.resume();
      child.on('error', () => reject(new Error('apk_scan_failed')));
      child.on('close', code => code === 0 ? resolve(JSON.parse(json)) : reject(new Error('apk_secret_or_url_scan_failed')));
      child.stdin.end(JSON.stringify(values));
    });
    await writeFile(new URL('../verification/apk-safety.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
    out(result);
    return;
  }
  if (!process.argv.includes('--deploy') && !process.argv.includes('--enable') && !process.argv.includes('--inspect-ledger') && !process.argv.includes('--code-only')) return;
  const runtimeRequire = createRequire(new URL('../wildflower-ai/package.json', import.meta.url));
  const sdk = runtimeRequire('@cloudbase/node-sdk');
  const db = sdk.init({ env: ENV, region: REGION, secretId: temporary.secretId, secretKey: temporary.secretKey, sessionToken: temporary.token, timeout: 10000 }).database();
  const ledger = db.collection(COLLECTION).doc('global-v1');
  const tcb = (Action, Param) => manager.commonService('tcb', '2018-06-08').call({ Action, Param: { EnvId: ENV, ...Param } });
  if (process.argv.includes('--inspect-ledger')) {
    const current = documentValue(await ledger.get());
    const deployed = await scf('GetFunction', { FunctionName: FUNCTION });
    const publicNames = ['AI_ENABLED', 'DEEPSEEK_MODEL', 'PUBLIC_BASE_PATH', 'DAILY_REQUEST_LIMIT', 'INSTALL_DAILY_LIMIT', 'GLOBAL_CONCURRENT_LIMIT', 'DAILY_BUDGET_MILLI_CNY', 'TOTAL_BUDGET_MILLI_CNY'];
    const settings = Object.fromEntries((deployed.Environment?.Variables || []).filter(x => publicNames.includes(x.Key)).map(x => [x.Key, x.Value]));
    const report = { stage: 'ledger', checkedAt: new Date().toISOString(), present: Boolean(current), schemaVersion: current?.schemaVersion, dailyRequests: current?.dailyRequests,
      dailyMilli: current?.dailyMilli, totalMilli: current?.totalMilli, sessionIssues: current?.sessionIssues, leases: Object.keys(current?.leases || {}).length,
      function: { name: deployed.FunctionName, status: deployed.Status, runtime: deployed.Runtime, type: deployed.Type, timeout: deployed.Timeout,
        memorySize: deployed.MemorySize, concurrency: deployed.InstanceConcurrencyConfig, settings },
      clientAcl: (await tcb('DescribeDatabaseACL', { CollectionName: COLLECTION })).AclTag,
      queriedLogServiceAvailable: Boolean(env.LogServices?.length || env.CustomLogServices?.length) };
    await mkdir(new URL('../verification/', import.meta.url), { recursive: true });
    await writeFile(new URL('../verification/ledger-final.json', import.meta.url), JSON.stringify(report, null, 2) + '\n');
    out(report);
    return;
  }
  let target;
  try { target = await scf('GetFunction', { FunctionName: FUNCTION }); }
  catch (e) { if (!String(e.code).includes('ResourceNotFound')) throw e; }
  if (process.argv.includes('--code-only')) {
    // Scoped protocol update: no key creation, config/route changes, health deploy or ledger reset.
    if (!target || target.Type !== 'HTTP' || target.Runtime !== 'Nodejs20.19') throw new Error('existing_function_config_conflict');
    const configurationHash = value => createHash('sha256').update(JSON.stringify({ environment: value.Environment,
      runtime: value.Runtime, timeout: value.Timeout, memory: value.MemorySize, concurrency: value.InstanceConcurrencyConfig })).digest('hex');
    const configurationBefore = configurationHash(target);
    const budgetBefore = documentValue(await ledger.get());
    const zip = await readFile(new URL('../dist/wildflower-ai.zip', import.meta.url));
    const manifest = JSON.parse(await readFile(new URL('../dist/package-manifest.json', import.meta.url), 'utf8'));
    if (createHash('sha256').update(zip).digest('hex') !== manifest.sha256) throw new Error('deployment_zip_hash_mismatch');
    await scf('UpdateFunctionCode', { FunctionName: FUNCTION, ZipFile: zip.toString('base64'), InstallDependency: 'FALSE' });
    await waitActive(scf);
    const after = await scf('GetFunction', { FunctionName: FUNCTION });
    const healthAfter = await scf('GetFunction', { FunctionName: 'wildflower-health' });
    const healthUnchanged = createHash('sha256').update(JSON.stringify({ code: healthAfter.CodeSha256, modified: healthAfter.ModTime, environment: healthAfter.Environment })).digest('hex') === healthFingerprint;
    const budgetAfter = documentValue(await ledger.get());
    if (configurationHash(after) !== configurationBefore || !healthUnchanged || budgetAfter.totalMilli < budgetBefore.totalMilli) throw new Error('code_only_preservation_check_failed');
    const report = { checkedAt: new Date().toISOString(), function: FUNCTION, status: after.Status, mode: 'code_only',
      healthUnchanged, configurationUnchanged: true, cumulativeBudgetNotReset: true, zipSha256: manifest.sha256 };
    await writeFile(new URL('../verification/culture-code-deployment.json', import.meta.url), JSON.stringify(report, null, 2));
    out(report); return;
  }
  if (process.argv.includes('--enable')) {
    if (!target || target.Type !== 'HTTP') throw new Error('target_function_missing');
    const next = (target.Environment?.Variables || []).map(v => v.Key === 'AI_ENABLED' ? { ...v, Value: 'true' } : v);
    if (!next.some(v => v.Key === 'AI_ENABLED')) throw new Error('pause_flag_missing');
    await scf('UpdateFunctionConfiguration', { FunctionName: FUNCTION, Environment: { Variables: next } });
    await waitActive(scf);
    for (let attempt = 0; attempt < 20; attempt++) {
      const response = await fetch(`https://${DOMAIN}/wildflower-ai/health`, { redirect: 'error', signal: AbortSignal.timeout(5000) });
      const state = await response.json();
      if (response.status === 200 && state.service === FUNCTION && state.status === 'ready') break;
      if (attempt === 19) throw new Error('enabled_revision_not_ready');
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    out({ stage: 'enabled', function: FUNCTION });
    return;
  }
  if (!variables.DEEPSEEK_API_KEY) throw new Error('health_provider_key_missing');
  if (!(tables.Collections || []).some(x => x.CollectionName === COLLECTION)) await manager.database.createCollection(COLLECTION);
  await tcb('ModifyDatabaseACL', { CollectionName: COLLECTION, AclTag: 'ADMINONLY' });
  const acl = await tcb('DescribeDatabaseACL', { CollectionName: COLLECTION });
  if (acl.AclTag !== 'ADMINONLY') throw new Error('client_acl_not_private');
  const seed = JSON.parse(await readFile(new URL('../quota-seed.json', import.meta.url), 'utf8')); delete seed._id;
  const initialized = await initializeLedger(db, COLLECTION, seed);
  out({ stage: 'database', initializedOnlyIfAbsent: initialized, acl: acl.AclTag, transactionCommitted: true });
  const targetVars = Object.fromEntries((target?.Environment?.Variables || []).map(x => [x.Key, x.Value]));
  let cached = {};
  try { cached = JSON.parse(await protectedText(await readFile(privateCache, 'utf8'), true)); } catch (e) {
    if (e.code !== 'ENOENT') throw new Error('protected_cache_unreadable');
  }
  let cloudbaseKey = targetVars.CLOUDBASE_APIKEY || variables.CLOUDBASE_APIKEY || cached.cloudbaseKey;
  if (!cloudbaseKey) {
    if (await protectedText(await protectedText('credential-protection-preflight'), true) !== 'credential-protection-preflight') throw new Error('credential_protection_failed');
    const key = await manager.env.createApiKey({ KeyType: 'api_key', KeyName: 'wildflower-ai-server', ExpireIn: 90 * 86400 });
    cloudbaseKey = key.ApiKey;
    if (!cloudbaseKey) throw new Error('server_api_key_missing');
    out({ stage: 'server_key_created', keyId: key.KeyId, expires: key.ExpireAt });
  }
  const signingKey = targetVars.SESSION_SIGNING_KEY || cached.signingKey || randomBytes(48).toString('base64url');
  await mkdir(join(process.env.LOCALAPPDATA, 'Wildflower', 'CloudBase'), { recursive: true });
  await writeFile(privateCache, await protectedText(JSON.stringify({ cloudbaseKey, signingKey })), { mode: 0o600 });
  // Prove the deployed server credential can access the protected ledger before publishing.
  const serverDb = sdk.init({ env: ENV, accessKey: cloudbaseKey, timeout: 10000 }).database();
  await serverDb.runTransaction(async tx => {
    const current = documentValue(await tx.collection(COLLECTION).doc('global-v1').get());
    if (current?.schemaVersion !== 1) throw new Error('ledger_missing');
    return true;
  });
  const config = JSON.parse(await readFile(new URL('../cloudbaserc.json', import.meta.url), 'utf8'));
  const deploymentVars = { ...targetVars, ...config.functions[0].envVariables, DEEPSEEK_API_KEY: variables.DEEPSEEK_API_KEY,
    CLOUDBASE_APIKEY: cloudbaseKey, SESSION_SIGNING_KEY: signingKey, AI_ENABLED: 'false' };
  const Environment = { Variables: Object.entries(deploymentVars).map(([Key, Value]) => ({ Key, Value })) };
  const zip = await readFile(new URL('../dist/wildflower-ai.zip', import.meta.url));
  const manifest = JSON.parse(await readFile(new URL('../dist/package-manifest.json', import.meta.url), 'utf8'));
  if (createHash('sha256').update(zip).digest('hex') !== manifest.sha256) throw new Error('deployment_zip_hash_mismatch');
  if (!target) {
    await scf('CreateFunction', { FunctionName: FUNCTION, Description: 'Wildflower Lens protected AI proxy', Runtime: 'Nodejs20.19', Type: 'HTTP',
      MemorySize: 256, Timeout: 60, Role: 'TCB_QcsRole', Stamp: 'MINI_QCBASE', InstallDependency: 'FALSE', Environment,
      InstanceConcurrencyConfig: { MaxConcurrency: 3, DynamicEnabled: 'FALSE' }, Code: { ZipFile: zip.toString('base64') } });
  } else {
    if (target.Type !== 'HTTP' || target.Runtime !== 'Nodejs20.19') throw new Error('existing_function_config_conflict');
    await scf('UpdateFunctionConfiguration', { FunctionName: FUNCTION, Environment, Timeout: 60, MemorySize: 256,
      InstanceConcurrencyConfig: { MaxConcurrency: 3, DynamicEnabled: 'FALSE' } });
    await waitActive(scf);
    await scf('UpdateFunctionCode', { FunctionName: FUNCTION, ZipFile: zip.toString('base64'), InstallDependency: 'FALSE' });
  }
  await waitActive(scf);
  const routes = await manager.env.describeHttpServiceRoute({ EnvId: ENV });
  const oldRoute = routes.Domains?.find(x => x.Domain === DOMAIN)?.Routes?.find(x => x.Path === '/wildflower-ai');
  if (oldRoute && (oldRoute.UpstreamResourceName !== FUNCTION || oldRoute.UpstreamResourceType !== 'WEB_SCF')) throw new Error('route_conflict');
  if (!oldRoute) {
    await manager.env.createHttpServiceRoute({ EnvId: ENV, Domain: { Domain: DOMAIN, Routes: [{ Path: '/wildflower-ai',
      UpstreamResourceType: 'WEB_SCF', UpstreamResourceName: FUNCTION, Enable: true, EnableAuth: false, EnableSafeDomain: false, EnablePathTransmission: true }] } });
  }
  const after = await scf('GetFunction', { FunctionName: 'wildflower-health' });
  if (createHash('sha256').update(JSON.stringify({ code: after.CodeSha256, modified: after.ModTime, environment: after.Environment })).digest('hex') !== healthFingerprint) throw new Error('health_changed');
  const report = { checkedAt: new Date().toISOString(), environment: ENV, function: FUNCTION, status: 'deployed_paused', databaseAcl: acl.AclTag,
    seedCreated: initialized, zipSha256: manifest.sha256, healthUnchanged: true, baseUrl: `https://${DOMAIN}/wildflower-ai` };
  await mkdir(new URL('../verification/', import.meta.url), { recursive: true });
  await writeFile(new URL('../verification/deployment.json', import.meta.url), JSON.stringify(report, null, 2) + '\n');
  out(report);
}

async function waitActive(scf) {
  for (let attempt = 0; attempt < 24; attempt++) {
    const state = await scf('GetFunction', { FunctionName: FUNCTION });
    if (state.Status === 'Active') return;
    if (['CreateFailed', 'UpdateFailed', 'Failed'].includes(state.Status)) throw new Error('function_not_active');
    await new Promise(resolve => setTimeout(resolve, 2500));
  }
  throw new Error('function_activation_timeout');
}

main().catch(e => { out({ stage: 'failed', code: e.code || (/^[a-z_]+$/.test(e.message) ? e.message : 'deployment_error'), action: e.action }); process.exitCode = 1; });
