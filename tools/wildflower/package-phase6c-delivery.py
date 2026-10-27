"""Package unedited device evidence, actual PNG exports and test results, with provenance."""
from pathlib import Path
import hashlib
import html
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
from collections import Counter
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'docs/wildflower/evidence/phase6c'
DEVICE = EVIDENCE / 'device'
SCREENS = ROOT / 'final-screenshots'
NAV = ROOT / 'navigation-flow'


def fingerprint(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    SCREENS.mkdir(exist_ok=True)
    NAV.mkdir(exist_ok=True)
    entries = []
    mapping = [
        ('01_explore_home', 'story/01-explore', '自然探索'),
        ('02_scan_camera', 'real-flow/scan-camera', '真实 CameraX · 虚拟植物场景'),
        ('03_identification_result', 'real-flow/identification-result', '实际 TFLite 识别结果'),
        ('04_my_garden', 'story/07-garden', '桂花摄影手记 · 花园'),
        ('05_plant_detail', 'story/08-personal-detail', '个人植物档案'),
        ('06_observation_gallery', 'story/10-gallery', '观察照片集'),
        ('07_observation_timeline', 'story/09-timeline', '观察时间轴'),
        ('08_seasons', 'story/13-seasons', '四季手账'),
        ('09_species_detail', 'story/03-species', '公共物种详情'),
        ('10_culture', 'story/04-culture', '植物文化 · Debug 展示规则'),
        ('11_culture_reading', 'story/05-culture-reading', '文化阅读 · Debug 展示规则'),
        ('12_observation_map', 'story/21-map-no-location', '观察地图 · 无坐标不补造标记'),
        ('13_poetry_editor', 'story/15-poetry-editor', '植物诗笺编辑器'),
        ('14_botanical_atlas', 'story/exports/botanical_atlas', '实际作品 · 植物图鉴'),
        ('15_classical_poetry', 'story/exports/classical_poetry', '实际作品 · 古典诗笺（无未批准引文）'),
        ('16_modern_journal', 'story/exports/modern_journal', '实际作品 · 自然手账'),
        ('17_settings_about', 'story/02-settings', '设置与帮助'),
        ('18_system_share', 'story/18-system-share', 'Android 系统分享'),
    ]
    for name, source, title in mapping:
        original = DEVICE / f'{source}.png'
        if '/exports/' in source:
            # MediaStore preserves earlier exports with (1)/(2) suffixes. Keep them
            # all in raw evidence and publish the latest real export, never overwrite originals.
            candidates = list(original.parent.glob(original.stem + '*.png'))
            if candidates:
                original = max(candidates, key=lambda path: path.stat().st_mtime_ns)
        target = SCREENS / f'{name}.png'
        # Never substitute a historical capture when a requested current capture is missing.
        if not original.is_file():
            raise FileNotFoundError(original)
        shutil.copy2(original, target)
        with Image.open(target) as im:
            im.verify()
        with Image.open(target) as im:
            pixels = list(im.size)
        kind = 'export' if '/exports/' in source else 'screenshot'
        if kind == 'export':
            assert pixels == [1600, 2400], (target, pixels)
        entries.append(dict(file=target.name, title=title, source=original.relative_to(ROOT).as_posix(),
                            kind=kind, pixels=pixels, sha256=fingerprint(target), build_type='debug'))

    flows = [
        ('01_scan_flow', 'CameraX → 实际 TFLite', ['real-flow/scan-camera', 'real-flow/identification-result']),
        ('02_result_flow', '识别 → 物种详情（尚未收藏）', ['real-flow/identification-result', 'real-flow/species-before-save']),
        ('03_garden_flow', '收藏 → 花园 → 个人档案', ['real-flow/garden-collected', 'real-flow/cover-updated']),
        ('04_gallery_flow', '照片集 → 重启后照片仍可读取', ['real-flow/cover-updated-gallery', 'real-flow/relaunch-gallery']),
        ('05_poetry_export_flow', '实际 PNG 导出 → SAF → 系统分享', ['real-flow/phase6-saf-saved', 'real-flow/phase6-share-chooser']),
        ('06_map_navigation', 'Room 合成坐标标记 → 对应档案（非野外 GPS）', ['capabilities/map-selected-synthetic-pin', 'capabilities/map-correct-archive']),
    ]
    font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 20)
    flow_manifest = []
    for name, title, sources in flows:
        sheet = Image.new('RGB', (850, 1010), '#f8f8f1')
        draw = ImageDraw.Draw(sheet)
        draw.text((22, 10), title, font=font, fill='#24452e')
        draw.text((22, 42), '真实 Debug 设备截屏拼接 · 非单张 App 页面 · 测试记录不作正式观察', font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 15), fill='#666666')
        for index, source in enumerate(sources):
            with Image.open(DEVICE / f'{source}.png') as im:
                thumb = ImageOps.contain(im.convert('RGB'), (395, 900), Image.Resampling.LANCZOS)
                sheet.paste(thumb, (20 + index * 420 + (395 - thumb.width) // 2, 85))
        target = NAV / f'{name}.png'
        sheet.save(target)
        flow_manifest.append(dict(file=target.name, title=title, sources=sources, sha256=fingerprint(target)))

    tests = {}
    for variant in ['debug', 'release']:
        paths = list((ROOT / f'app/build/test-results/test{variant.title()}UnitTest').glob('TEST-*.xml'))
        totals = Counter()
        for path in paths:
            suite = ET.parse(path).getroot()
            totals.update({key: int(suite.get(key, '0')) for key in ['tests', 'failures', 'errors', 'skipped']})
            destination = EVIDENCE / f'tests/jvm-{variant}' / path.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)
        tests[f'jvm_{variant}'] = dict(totals)
    lint_path = ROOT / 'app/build/reports/lint-results-release.xml'
    if lint_path.exists():
        lint = ET.parse(lint_path).getroot()
        tests['lint_release'] = dict(Counter(issue.get('severity') for issue in lint.findall('issue')))
        (EVIDENCE / 'tests').mkdir(exist_ok=True)
        shutil.copy2(lint_path, EVIDENCE / 'tests/lint-release.xml')
    device_tests = EVIDENCE / 'tests/device-full.xml'
    if device_tests.exists():
        root = ET.parse(device_tests).getroot()
        suites = [root] if root.tag == 'testsuite' else list(root.iter('testsuite'))
        totals = Counter()
        failures = []
        for suite in suites:
            totals.update({key: int(suite.get(key, '0')) for key in ['tests', 'failures', 'errors', 'skipped']})
            failures.extend(f'{case.get("classname")}#{case.get("name")}' for case in suite.findall('testcase') if case.find('failure') is not None or case.find('error') is not None)
        tests['device_debug_full'] = dict(totals, failed_cases=failures)
    tests['additional_debug_checks'] = {}
    for name in ['cold-seed-real-flow', 'cold-relaunch-verification', 'plantnet-real-camera',
                 'network-offline-fallback', 'google-map-marker-navigation',
                 'google-map-network-error', 'presentation-screens-clean-chooser']:
        path = EVIDENCE / f'{name}.txt'
        if path.exists():
            output = path.read_text(encoding='utf-8-sig')
            tests['additional_debug_checks'][name] = 'PASS' if re.search(r'OK \(\d+ tests?\)', output) else 'FAIL_OR_BLOCKED'
        else:
            tests['additional_debug_checks'][name] = 'NOT_EXECUTED'
    release = ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    destination = ROOT / 'final-delivery'
    destination.mkdir(exist_ok=True)
    shutil.copy2(release, destination / release.name)
    debug = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    if debug.exists():
        shutil.copy2(debug, destination / 'app-debug-verification-only.apk')
    catalog = json.loads((ROOT / 'app/src/main/assets/culture/culture_entries.json').read_text(encoding='utf-8'))['entries']
    manifest = dict(
        status='PARTIALLY_COMPLETE_NOT_READY_FOR_SUBMISSION', app_name='草木知时', package='com.floralens',
        git_head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        release=dict(file='final-delivery/' + release.name, bytes=release.stat().st_size, sha256=fingerprint(release), signing='Release signing unavailable', install='BLOCKED_UNSIGNED'),
        debug_sha256=fingerprint(debug) if debug.exists() else None,
        culture_approved_for_publication=sum(item['publicationStatus'] == 'approved_for_publication' for item in catalog),
        tests=tests, entries=entries, navigation=flow_manifest,
        notes=['All runtime captures are Debug, NOT installed Release.', 'Licensed photography notebook uses staged dates, not same-tree campus growth evidence.', 'CameraX/TFLite captures are actual model execution against the emulator virtual scene.', 'Map screenshot honestly has no GPS; separate regression uses explicitly synthetic coordinates.', 'Release culture contains no approved entries; do not present Debug culture as available Release content.'])
    (SCREENS / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    cards = ''.join(f'<figure><a href="{entry["file"]}"><img loading="lazy" src="{entry["file"]}" alt="{html.escape(entry["title"])}"></a><figcaption>{html.escape(entry["title"])}<small>{entry["pixels"][0]} × {entry["pixels"][1]}</small></figcaption></figure>' for entry in entries)
    (SCREENS / 'index.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>草木知时 · 最终交付检查</title><style>body{background:#f8f8f1;color:#24452e;font:16px system-ui;margin:28px}p{max-width:1100px;line-height:1.8}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:24px}figure{margin:0;padding:12px;background:#fffdf8;border:1px solid #dce6d6;border-radius:12px}img{width:100%;height:510px;object-fit:contain}figcaption{line-height:1.8}small{display:block;color:#777}</style><h1>草木知时 · Wildflower Lens</h1><p>本轮 API 35 真实 Debug 截图与三套实际导出作品。Release 尚无签名，不能把这些截图称为已安装 Release 的验收。桂花摄影手记使用许可摄影和演示日期，不作校园实拍或同株生长史证明。文化阅读为 Debug 展示，当前 Release 无批准文化条目。地图没有坐标时不虚构位置。</p><p><a href="manifest.json">来源、指纹与测试摘要</a> · <a href="../docs/wildflower/evidence/phase6c/RESULTS.md">交付结果</a> · <a href="../DEMO_FLOW.md">演示流程</a> · <a href="../TECHNICAL_OVERVIEW.md">技术概要</a></p><div class="grid">' + cards + '</div></html>', encoding='utf-8')
    print(json.dumps(dict(screenshots=len(entries), navigation=len(flows), release=manifest['release'], tests=tests), ensure_ascii=False))


if __name__ == '__main__':
    main()
