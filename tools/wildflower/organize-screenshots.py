"""Non-destructive, byte-identical review copies of existing app evidence."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
import hashlib
import html
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'docs/wildflower/evidence'
OUT = EVIDENCE / 'all-pages-review'
V2 = 'visual-kit-v2/screenshots'
GARDEN = 'my-garden/final'
PERSONAL = 'personal-detail/final'
POETRY = 'poetry-module/final'
GROUPS = [
    ('01-core-pages', '十个核心页面', [
        ('01-explore', '自然探索首页', V2, 'explore.png'),
        ('02-garden-empty', '我的花园 · 空收藏', GARDEN, 'garden-empty-393.png'),
        ('03-garden-populated', '我的花园 · 植物标本墙', GARDEN, 'garden-populated-393.png'),
        ('04-personal-detail', '我的植物档案', PERSONAL, 'personal-detail-393.png'),
        ('05-species-detail', '物种详情', V2, 'species-detail.png'),
        ('06-seasons', '四季手账', V2, 'seasons.png'),
        ('07-culture', '植物文化 · 开发版审阅', V2, 'culture.png'),
        ('08-gallery', '观察照片集', V2, 'observation-gallery.png'),
        ('09-scan', '相机识别 · 模拟器相机', V2, 'scan.png'),
        ('10-poetry-editor', '植物诗笺编辑器 · 首屏', POETRY, 'editor-393-top.png'),
    ]),
    ('02-related-pages-and-states', '关联页面与状态', [
        ('01-identification-result', '识别结果', V2, 'identification-result.png'),
        ('02-observation-editor', '添加 / 编辑观察', V2, 'observation-editor.png'),
        ('03-observation-fields', '观察日期与备注', V2, 'observation-editor-fields.png'),
        ('04-photo-preview', '观察照片预览', PERSONAL, 'personal-photo-preview-393.png'),
        ('05-personal-timeline', '个人档案 · 摄影时间轴', PERSONAL, 'personal-timeline-393.png'),
        ('06-personal-no-photo', '个人档案 · 暂无照片', PERSONAL, 'personal-empty-393.png'),
        ('07-personal-cover-updated', '个人档案 · 更新封面后', PERSONAL, 'personal-cover-updated-393.png'),
        ('08-personal-creation', '个人档案 · 诗笺创作入口', PERSONAL, 'personal-creation-idle.png'),
        ('09-garden-bottom', '花园 · 列表底部', GARDEN, 'garden-bottom-393.png'),
        ('10-explore-featured', '探索 · 精选植物', V2, 'explore-featured.png'),
        ('11-culture-reading', '文化 · 资料阅读', V2, 'culture-reading.png'),
        ('12-culture-empty', '文化 · 无资料状态（状态测试）', V2, 'culture-empty.png'),
        ('13-seasons-autumn', '季节详情 · 秋季', V2, 'seasons-autumn.png'),
        ('14-seasons-winter-empty', '季节详情 · 冬季无观察', V2, 'seasons-winter-empty.png'),
        ('15-seasons-empty', '四季手账 · 空状态', V2, 'seasons-empty.png'),
        ('16-poetry-complete', '诗笺 · 完整扩展视口（非普通手机高度）', POETRY, 'editor-393-complete.png'),
        ('17-poetry-templates', '诗笺 · 三套版式选择', POETRY, 'editor-393-templates.png'),
        ('18-system-photo-picker', '系统照片选择器', POETRY, 'photo-picker.png'),
        ('19-system-share', 'Android 系统分享', POETRY, 'system-share.png'),
        ('20-scan-permission-denied', '相机权限拒绝状态（较早取证）', 'phase5-final/screenshots', 'scan-permission-denied.png'),
    ]),
    ('03-responsive-320dp', '320dp 窄屏与边界状态', [
        ('01-explore', '探索 · 320dp', V2, 'explore-320.png'),
        ('02-garden-empty', '空花园 · 320dp', GARDEN, 'garden-empty-320.png'),
        ('03-garden-populated', '植物标本墙 · 320dp', GARDEN, 'garden-populated-320.png'),
        ('04-garden-long-missing', '花园 · 长名称 / 缺图', GARDEN, 'garden-missing-long-320.png'),
        ('05-garden-large-font', '花园 · 放大字体', GARDEN, 'garden-large-font-320.png'),
        ('06-personal-detail', '个人档案 · 320dp', PERSONAL, 'personal-detail-320.png'),
        ('07-personal-timeline', '个人时间轴 · 320dp', PERSONAL, 'personal-timeline-320.png'),
        ('08-personal-long-name', '个人档案 · 长名称', PERSONAL, 'personal-one-long-320.png'),
        ('09-personal-large-font', '个人档案 · 放大字体', PERSONAL, 'personal-large-font-320.png'),
        ('10-personal-missing', '个人档案 · 缺图', PERSONAL, 'personal-missing-320.png'),
        ('11-species-offline', '物种详情 · 离线 / 缺图（状态测试）', V2, 'species-offline-broken-320.png'),
        ('12-culture', '文化 · 320dp', V2, 'culture-320.png'),
        ('13-seasons', '四季手账 · 320dp', V2, 'seasons-320.png'),
        ('14-gallery', '照片集 · 320dp', V2, 'observation-gallery-320.png'),
        ('15-observation-long-note', '观察编辑 · 长备注', V2, 'observation-editor-long-note-320.png'),
        ('16-scan', '相机 · 320dp', V2, 'scan-320.png'),
        ('17-poetry-editor', '诗笺首屏 · 320dp', POETRY, 'editor-320-top.png'),
        ('18-poetry-templates', '诗笺选择版式 · 320dp', POETRY, 'editor-320-templates.png'),
    ]),
    ('04-tablet', '平板布局', [
        ('01-culture', '文化 · 平板', V2, 'culture-tablet.png'),
        ('02-seasons', '四季手账 · 平板', V2, 'seasons-tablet.png'),
    ]),
    ('05-legacy-secondary-pages', '二级功能 · 历史截图（非当前视觉）', [
        ('01-map', '观察地图 · Phase 1 历史截图', 'phase1', 'map.png'),
        ('02-settings', '设置 · Phase 1 历史截图', 'phase1', 'settings.png'),
        ('03-care', '养护 · Phase 1 历史截图', 'phase1', 'care.png'),
        ('04-leaf-health', '叶片健康 · Phase 1 历史截图', 'phase1', 'leaf-health.png'),
    ]),
    ('06-exported-artworks', '实际导出作品 · 非页面截图', [
        ('01-botanical-atlas', '植物学图鉴 · 1600×2400', 'poetry-module/exports', 'botanical-atlas.png'),
        ('02-classical-poetry', '古典文化诗笺 · 无批准诗词回退版', 'poetry-module/exports', 'classical-poetry.png'),
        ('03-modern-journal', '现代自然手账 · 1600×2400', 'poetry-module/exports', 'modern-journal.png'),
    ]),
]


def safe_copy(src, dest):
    if dest.exists():
        if dest.read_bytes() != src.read_bytes():
            raise RuntimeError(f'Refusing to overwrite modified file: {dest}')
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)


def new_or_equal(path, data):
    if path.exists() and path.read_bytes() != data:
        raise RuntimeError(f'Refusing to overwrite modified artifact: {path}')
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        path.write_bytes(data)


def main():
    entries = []
    # Validate every source before creating the collection.
    for folder, section, items in GROUPS:
        for slug, title, source_dir, filename in items:
            src = EVIDENCE / source_dir / filename
            with Image.open(src) as im:
                im.verify()
            with Image.open(src) as im:
                size = im.size
            entries.append(dict(section=section, title=title,
                source=src.relative_to(ROOT).as_posix(),
                file=f'{folder}/{slug}.png', width=size[0], height=size[1],
                sha256=hashlib.sha256(src.read_bytes()).hexdigest()))
    for entry in entries:
        safe_copy(ROOT / entry['source'], OUT / entry['file'])

    note = ('本集合整理已有真实运行证据，不是本次重新拍摄，也不是同一 APK 的全页截图。'
            '核心页按最近改造版本选取；二级功能旧截图单独归档。'
            '参考摄影测试档案通过真实 Room / PhotoStore 存取，但不是用户野外实拍或同株生长史。'
            '文化页面含开发版资料审阅，未批准资料不代表已核实种级事实。'
            '截图未裁切、修图或移除测试标识；总览仅按比例缩小。')
    md = ['# Wildflower Lens · 全页面截图索引', '', note, '',
          '整理日期：2026-10-13。原始证据完整保留；每份副本 SHA-256 与来源一致。', '',
          '- `index.html`：离线浏览，点击截图打开原尺寸图片。',
          '- `overview-core.png`：十个核心页面总览（仅缩放排版，非单张运行截图）。',
          '- `manifest.json`：逐张来源、尺寸与 SHA-256。', '',
          '## 范围限制', '',
          '地图、设置、养护、叶片健康目前仅收录 Phase 1 历史截图，不能代表最新视觉。'
          '本集合不宣称所有弹窗、系统页面或错误分支都已覆盖。'
          '诗笺完整页来自加高模拟器视口，普通手机需要滚动。', '',
          '## 摄影与原始说明', '',
          '- 摄影署名及许可：[原素材台账](provenance/ASSET_ATTRIBUTION.md)。',
          '- 来源说明：[V2](provenance/evidence/visual-kit-v2/README.md)、[花园](provenance/evidence/my-garden/README.md)、'
          '[个人档案](provenance/evidence/personal-detail/README.md)、[诗笺](provenance/evidence/poetry-module/README.md)。',
          '- 诗笺摄影含 Kenraiz / CC BY-SA 4.0、Forest & Kim Starr / CC BY 2.0；'
          '个人档案另含 Achillu / CC BY-SA 3.0；不移除图片内署名。',
          '- 三张导出作品为实际 MediaStore PNG；古典模板保留无批准诗词的安全回退。', '']
    sections = []
    for folder, section, _ in GROUPS:
        subset = [e for e in entries if e['section'] == section]
        md += [f'## {section}', '', '| 页面 / 状态 | 原图 | 来源 |', '|---|---|---|']
        cards = []
        for e in subset:
            md.append(f"| {e['title']} | [PNG · {e['width']}×{e['height']}]({e['file']}) | `{e['source']}` |")
            cards.append(f'<figure><a href="{e["file"]}"><img loading="lazy" src="{e["file"]}" alt="{html.escape(e["title"])}"></a>'
                         f'<figcaption>{html.escape(e["title"])}<small>{e["width"]} × {e["height"]}<br>{html.escape(e["source"])}</small></figcaption></figure>')
        md.append('')
        sections.append(f'<section><h2>{html.escape(section)}</h2><div class="grid">{"".join(cards)}</div></section>')
    page = ('<!doctype html><html lang="zh-CN"><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>Wildflower Lens 全页面截图</title><style>'
            'body{margin:0;background:#f8f8f1;color:#24452e;font-family:system-ui,"Microsoft YaHei",sans-serif;}'
            'main{max-width:1440px;margin:auto;padding:32px;}h1{font-size:28px;}p{max-width:1000px;line-height:1.8;}'
            'section{margin-top:40px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:24px;}'
            'figure{margin:0;padding:12px;background:#fffdf8;border:1px solid #dce6d6;border-radius:10px;}'
            'img{display:block;width:100%;height:470px;object-fit:contain;background:#eeeee6;}'
            'figcaption{margin-top:12px;line-height:1.6}small{display:block;color:#777;font-size:11px;overflow-wrap:anywhere;}'
            'a{color:#24452e}h2{font-size:22px}@media(max-width:520px){main{padding:16px}img{height:400px}}'
            '</style><main><h1>Wildflower Lens · 全页面截图</h1>'
            f'<p>{html.escape(note)}</p><p>2026-10-13 · <a href="README.md">来源与范围说明</a> · '
            '<a href="overview-core.png">十屏总览</a> · <a href="manifest.json">原图清单</a></p>'
            + ''.join(sections) + '</main></html>')
    new_or_equal(OUT / 'README.md', ('\n'.join(md) + '\n').encode('utf-8'))
    new_or_equal(OUT / 'index.html', page.encode('utf-8'))
    new_or_equal(OUT / 'manifest.json', (json.dumps(entries, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
    # Only the overview is composed; every screenshot copy remains byte-identical.
    sheet = Image.new('RGB', (1560, 1570), '#f8f8f1')
    draw = ImageDraw.Draw(sheet)
    font_file = 'C:/Windows/Fonts/msyh.ttc'
    heading = ImageFont.truetype(font_file, 30)
    label = ImageFont.truetype(font_file, 19)
    small = ImageFont.truetype(font_file, 16)
    draw.text((28, 20), 'Wildflower Lens · 十个核心页面', fill='#24452e', font=heading)
    draw.text((28, 65), '跨阶段真实截图汇总 / 非同一构建新拍 / 参考摄影档案非用户野外实拍', fill='#6a7364', font=small)
    for i, e in enumerate(entries[:10]):
        x = 28 + i % 5 * 305
        y = 108 + i // 5 * 715
        with Image.open(OUT / e['file']) as im:
            thumb = ImageOps.contain(im.convert('RGB'), (275, 615), Image.Resampling.LANCZOS)
            sheet.paste(thumb, (x + (275 - thumb.width) // 2, y))
        draw.text((x, y + 627), f'{i + 1:02d} {e["title"].split(" · ")[0]}', fill='#24452e', font=label)
        draw.text((x, y + 659), f'{e["width"]}×{e["height"]} · 原图见目录', fill='#6a7364', font=small)
    import io
    buf = io.BytesIO()
    sheet.save(buf, format='PNG')
    new_or_equal(OUT / 'overview-core.png', buf.getvalue())

    # Include provenance documents with the downloadable set, not private data.
    for relative in ['ASSET_ATTRIBUTION.md', 'evidence/visual-kit-v2/README.md',
                     'evidence/my-garden/README.md', 'evidence/personal-detail/README.md',
                     'evidence/poetry-module/README.md', 'evidence/poetry-module/ASSETS.md']:
        src = ROOT / 'docs/wildflower' / relative
        safe_copy(src, OUT / 'provenance' / relative)
    zip_path = EVIDENCE / 'Wildflower_All_Pages_Review_2026-10-13.zip'
    if zip_path.exists():
        raise RuntimeError(f'Refusing to overwrite ZIP: {zip_path}')
    with zipfile.ZipFile(zip_path, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=3) as archive:
        for p in sorted(OUT.rglob('*')):
            if p.is_file():
                archive.write(p, Path(OUT.name) / p.relative_to(OUT))
    with zipfile.ZipFile(zip_path) as archive:
        assert archive.testzip() is None
    print(json.dumps({'screenshots': len(entries) - 3, 'artworks': 3,
                      'output': str(OUT), 'zip': str(zip_path),
                      'zip_bytes': zip_path.stat().st_size}, ensure_ascii=False))


if __name__ == '__main__':
    main()
