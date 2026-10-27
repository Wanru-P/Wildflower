"""Index untouched emulator captures and actual exported artwork; no app/UI mutations."""
from pathlib import Path
import hashlib
import html
import json
import subprocess
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/wildflower/evidence/phase6b'
TITLES = {
    '01-explore': '自然探索首页', '02-settings': '设置与帮助', '03-species': '物种详情',
    '04-culture': '植物文化', '05-culture-reading': '文化阅读', '06-garden-empty': '空花园',
    '07-garden': '桂花摄影手记 · 花园', '08-personal-detail': '个人植物档案', '09-timeline': '观察时间轴',
    '10-gallery': '观察照片集', '11-photo-detail': '单张照片详情', '12-observation-editor': '观察编辑',
    '13-seasons': '四季手账', '14-autumn': '秋季记录', '15-poetry-editor': '植物诗笺编辑器',
    '16-select-photo': '选择观察照片', '17-template-botanical_atlas': '植物图鉴',
    '17-template-classical_poetry': '古典诗笺', '17-template-modern_journal': '自然手账',
    '18-system-share': '系统分享', '19-identification-result': '真实相机 / 模型识别 · 中文展示',
    '20-scan': '真实 CameraX 预览 · 虚拟植物场景',
    'botanical_atlas': '实际作品 · 植物图鉴', 'classical_poetry': '实际作品 · 古典诗笺',
    'modern_journal': '实际作品 · 自然手账',
}
CHANGED = [
    'app/build.gradle.kts', 'app/src/main/java/com/floralens/data/culture/CultureCatalog.kt',
    'app/src/main/java/com/floralens/domain/card/CardTemplate.kt',
    'app/src/main/java/com/floralens/reminders/WateringWorker.kt',
    *['app/src/main/java/com/floralens/ui/' + name for name in [
        'components/BotanicalFurniture.kt', 'navigation/FloraLensNavHost.kt',
        'presentation/PlantDisplayNames.kt', 'presentation/ProductMessages.kt',
        'screens/card/BotanicalCardScreen.kt', 'screens/card/BotanicalCardViewModel.kt',
        'screens/care/CareScreen.kt', 'screens/care/CareViewModel.kt',
        'screens/culture/CultureScreen.kt', 'screens/culture/CultureReadingCopy.kt',
        'screens/details/PlantDetailsScreen.kt', 'screens/details/PlantDetailsViewModel.kt',
        'screens/explore/ExploreScreen.kt', 'screens/identify/IdentifyScreen.kt',
        'screens/identify/IdentifyViewModel.kt', 'screens/identify/ResultSheet.kt',
        'screens/leafhealth/LeafHealthScreen.kt', 'screens/map/MapScreen.kt',
        'screens/observation/ObservationEditorScreen.kt', 'screens/observation/ObservationEditorViewModel.kt',
        'screens/observation/ObservationGalleryScreen.kt', 'screens/plantdetail/PersonalDetailComponents.kt',
        'screens/plantdetail/PlantDetailViewModel.kt']],
    'app/src/main/res/values/strings.xml',
    *['app/src/androidTest/java/com/floralens/' + name + '.kt' for name in [
        'CultureCatalogTest', 'CultureStateTest', 'Phase1NavigationTest', 'Phase2JourneyTest',
        'Phase3JourneyTest', 'Phase4JourneyTest', 'Phase6ACapabilitiesTest', 'Phase6AOnlineChecks',
        'Phase6BProductStoryTest', 'IdentificationProductCopyTest', 'ResultSheetTest']],
    *['app/src/test/java/com/floralens/' + name + '.kt' for name in [
        'CareViewModelTest', 'IdentifyViewModelTest', 'PlantDisplayNamesTest']],
    'docs/wildflower/phase6b-copy.md', 'tools/wildflower/package-phase6b-evidence.py',
]


def main():
    entries = []
    for name in TITLES:
        p = OUT / (('exports/' if name in {'botanical_atlas', 'classical_poetry', 'modern_journal'} else '') + name + '.png')
        current = OUT / 'final-runtime' / (name + '.png')
        # Keep the clean, genuine earlier chooser capture; later Android ranking can surface
        # the instrumentation receiver. Neither original is edited or erased.
        if name != '18-system-share' and current.exists():
            p = current
        if not p.exists():
            continue
        with Image.open(p) as im:
            im.verify()
        with Image.open(p) as im:
            size = im.size
        if p.parent.name == 'exports':
            assert size == (1600, 2400)
        entries.append(dict(title=TITLES[p.stem], file=p.relative_to(OUT).as_posix(),
                            pixels=list(size), sha256=hashlib.sha256(p.read_bytes()).hexdigest(),
                            kind='export' if p.parent.name == 'exports' else 'screenshot'))
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    manifest = dict(apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                    git_head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                    working_tree='dirty; original work preserved; no commit or push', entries=entries)
    (OUT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (OUT / 'CHANGED_FILES.md').write_text('# Phase 6B 修改文件\n\n包含与已有未提交修改叠加的文件，不覆盖原工作。\n\n' +
        '\n'.join(f'- [{p}]({(ROOT / p).as_posix()})' for p in CHANGED) + '\n', encoding='utf-8')
    note = ('本阶段分次取得的 API 35 实际运行截图与真正导出的 PNG，原图未裁切或修饰。桂花手记采用既有许可摄影，'
            '展示日期不代表原始拍摄日期，也不代表同株校园生长史；摄影署名保留。'
            '真实 CameraX / 模型识别截图单独标明，不能把摄影手记测试代替真实识别测试。')
    cards = ''.join(f'<figure><a href="{e["file"]}"><img loading="lazy" src="{e["file"]}" alt="{html.escape(e["title"])}"></a>'
                    f'<figcaption>{html.escape(e["title"])}<small>{e["pixels"][0]} × {e["pixels"][1]}</small></figcaption></figure>'
                    for e in entries)
    (OUT / 'index.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1"><title>Wildflower Lens · Phase 6B</title>'
        '<style>body{background:#f8f8f1;color:#24452e;font:16px system-ui;margin:28px}p{max-width:1100px;line-height:1.8}'
        '.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:24px}'
        'figure{margin:0;padding:12px;background:#fffdf8;border:1px solid #dce6d6;border-radius:12px}'
        'img{width:100%;height:510px;object-fit:contain}figcaption{line-height:1.8}small{display:block;color:#777}a{color:inherit}</style>'
        '<h1>Wildflower Lens · 产品化整理</h1><p>' + note + '</p>'
        '<p><a href="CHANGED_FILES.md">修改文件</a> · <a href="../../phase6b-copy.md">关键文案与素材边界</a> · '
        '<a href="manifest.json">原图清单与 APK 指纹</a> · <a href="TEST_RESULTS.md">测试结果</a></p>'
        '<div class="grid">' + cards + '</div></html>', encoding='utf-8')
    selected = [next(e for e in entries if Path(e['file']).stem == name) for name in [
        '01-explore', '07-garden', '08-personal-detail', '10-gallery', '13-seasons',
        '04-culture', '15-poetry-editor', '17-template-botanical_atlas',
        '17-template-classical_poetry', '17-template-modern_journal']]
    sheet = Image.new('RGB', (1500, 1550), '#f8f8f1')
    draw = ImageDraw.Draw(sheet)
    font = 'C:/Windows/Fonts/msyh.ttc'
    draw.text((25, 14), 'Wildflower Lens · Phase 6B 产品化截图', font=ImageFont.truetype(font, 27), fill='#24452e')
    draw.text((25, 55), '真实运行截图缩略总览 · 摄影手记展示日期不作实拍日期或校园地点证明', font=ImageFont.truetype(font, 17), fill='#6a7364')
    for i, e in enumerate(selected):
        x, y = 25 + i % 5 * 295, 100 + i // 5 * 720
        with Image.open(OUT / e['file']) as im:
            thumb = ImageOps.contain(im.convert('RGB'), (265, 620), Image.Resampling.LANCZOS)
            sheet.paste(thumb, (x + (265 - thumb.width) // 2, y))
        draw.text((x, y + 632), e['title'], font=ImageFont.truetype(font, 18), fill='#24452e')
    sheet.save(OUT / 'overview.png')
    print(json.dumps(dict(screenshots=sum(e['kind']=='screenshot' for e in entries), exports=3,
                         changed_files=len(CHANGED), index=str(OUT / 'index.html')), ensure_ascii=False))


if __name__ == '__main__':
    main()
