import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const run = promisify(execFile);
const device = process.env.ANDROID_SERIAL || 'emulator-5554';
const directory = fileURLToPath(new URL('../verification/release-ui/', import.meta.url));
const adb = async (...args) => (await run('adb', ['-s', device, ...args], { timeout: 45000, maxBuffer: 2 * 1024 * 1024 })).stdout;
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
function decode(value) { return value.replaceAll('&quot;', '"').replaceAll('&apos;', "'").replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&').replaceAll('&#10;', '\n'); }
export async function nodes() {
  await adb('shell', 'uiautomator', 'dump', '/sdcard/wildflower-cloudbase-ui.xml');
  const xml = await adb('shell', 'cat', '/sdcard/wildflower-cloudbase-ui.xml');
  return [...xml.matchAll(/<node\b([^>]+)>?/g)].map(match => Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(x => [x[1], decode(x[2])])));
}
async function tap(node) {
  if (!node) throw new Error('ui_target_not_found');
  const numbers = node.bounds.match(/\d+/g).map(Number);
  await adb('shell', 'input', 'tap', String(Math.floor((numbers[0] + numbers[2]) / 2)), String(Math.floor((numbers[1] + numbers[3]) / 2)));
  await pause(450);
}
async function waitFor(predicate) {
  for (let attempt = 0; attempt < 6; attempt++) {
    const node = (await nodes()).find(predicate);
    if (node) return node;
    await pause(400);
  }
  throw new Error('ui_target_not_found');
}
async function snapshot(name) {
  await mkdir(directory, { recursive: true });
  await adb('shell', 'screencap', '-p', '/sdcard/wildflower-cloudbase-screen.png');
  await adb('pull', '/sdcard/wildflower-cloudbase-screen.png', `${directory}/${name}.png`);
}
async function main() {
  if (process.argv.includes('--inspect')) {
    console.log(JSON.stringify((await nodes()).filter(x => x.text || x['content-desc'] || x.class === 'android.widget.EditText')
      .map(x => ({ text: x.text, description: x['content-desc'], type: x.class, bounds: x.bounds, clickable: x.clickable })), null, 2));
  } else if (process.argv.includes('--snapshot')) {
    await snapshot(process.argv[process.argv.indexOf('--snapshot') + 1]);
  } else if (process.argv.includes('--tap')) {
    const label = process.argv[process.argv.indexOf('--tap') + 1];
    await tap(await waitFor(x => x.text === label || x['content-desc'] === label));
  } else if (process.argv.includes('--type')) {
    const text = process.argv[process.argv.indexOf('--type') + 1];
    if (!/^[A-Za-z0-9 .?,!-]+$/.test(text)) throw new Error('only_safe_ascii_input');
    await tap(await waitFor(x => x.class === 'android.widget.EditText'));
    await adb('shell', 'input', 'text', text.replaceAll(' ', '%s'));
  } else if (process.argv.includes('--scroll')) {
    const node = (await nodes()).find(x => x.scrollable === 'true' && x.bounds);
    if (!node) throw new Error('scroll_container_not_found');
    const [left, top, right, bottom] = node.bounds.match(/\d+/g).map(Number);
    const x = Math.floor((left + right) / 2), height = bottom - top;
    await adb('shell', 'input', 'swipe', String(x), String(Math.floor(top + height * .8)), String(x), String(Math.floor(top + height * .25)), '500');
  }
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
