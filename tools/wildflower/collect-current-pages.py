"""Collect unmodified runtime screenshots; preserve previous review deliveries."""
import hashlib
import html
import importlib.util
import io
import json
from pathlib import Path
import zipfile

from PIL import Image, ImageDraw, ImageFont, ImageOps

SPEC = importlib.util.spec_from_file_location('previous_collection', Path(__file__).with_name('organize-screenshots.py'))
previous = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(previous)
ROOT, EVIDENCE = previous.ROOT, previous.EVIDENCE
OUT = EVIDENCE / 'all-pages-2026-10-14'
ZIP = EVIDENCE / 'Wildflower_All_Pages_2026-10-14.zip'
D = 'current-pages-2026-10-14/device'
V = previous.V2
ONLINE = 'phase6a/online'
GALLERY = 'observation-gallery/final'

CORE = [
    ('01-explore', '自然探索首页', D, 'explore-current.png'),
    ('02-scan', '相机识别 · 模拟器虚拟场景', D, 'scan-settled.png'),
    ('03-result', '真实 Pl@ntNet 识别结果', ONLINE, 'plantnet-camera-result.png'),
    ('04-species', '物种详情 · 编辑推荐', V, 'species-detail.png'),
    ('05-capture-species', '本次识别 · 物种详情', ONLINE, 'plantnet-species-no-auto-collection.png'),
    ('06-garden-empty', '我的花园 · 空收藏', D, 'garden-empty-current.png'),
    ('07-garden', '我的花园 · 标本墙', previous.GARDEN, 'garden-populated-393.png'),
    ('08-plant', '我的植物档案', previous.PERSONAL, 'personal-detail-393.png'),
    ('09-editor', '添加与编辑观察', V, 'observation-editor.png'),
    ('10-gallery', '观察照片集 · 最新改造', GALLERY, 'gallery-393.png'),
    ('11-photo', '单张观察照片详情', GALLERY, 'photo-detail.png'),
    ('12-seasons', '四季手账', V, 'seasons.png'),
    ('13-season', '季节详情 · 秋季', V, 'seasons-autumn.png'),
    ('14-culture', '植物文化 · 开发版审阅', V, 'culture.png'),
    ('15-poetry', '植物诗笺编辑器', previous.POETRY, 'editor-393-top.png'),
    ('16-online-map', '在线观察地图 · 测试坐标', ONLINE, 'google-map-selected-room-pin.png'),
    ('17-offline-map', '离线观察地图 · 无坐标状态', D, 'map.png'),
    ('18-settings', '设置与关于', D, 'settings-current.png'),
    ('19-care', '养护提醒', D, 'care.png'),
    ('20-leaf', '叶片健康', D, 'leaf-health.png'),
    ('21-onboarding', '首次启动 · 欢迎引导', 'phase6a', 'final-start.png'),
    ('22-review', '文化资料审阅 · 仅 Debug', D, 'culture-review.png'),
]

RELATED = [item for item in previous.GROUPS[1][2] if item[0] != '01-identification-result']
RELATED += [
    ('21-science', '物种详情 · 已缓存科学知识', D, 'species-cached-science.png'),
    ('22-collect', '首次收藏 · 确认弹窗', D, 'collect-confirmation.png'),
    ('23-existing', '收藏 · 选择已有植株', D, 'existing-plant-choice.png'),
    ('24-gallery-empty', '观察照片集 · 空状态', GALLERY, 'gallery-empty-393.png'),
    ('25-gallery-cover', '观察照片集 · 设置封面后', GALLERY, 'gallery-cover-updated.png'),
    ('26-atlas', '诗笺 · 植物学图鉴预览', D, 'botanical-atlas-preview.png'),
    ('27-classical', '诗笺 · 古典文化预览（无批准诗词回退）', D, 'classical-poetry-preview.png'),
    ('28-journal', '诗笺 · 现代自然手账预览', D, 'modern-journal-preview.png'),
    ('29-card-no-photo', '诗笺 · 暂无观察照片', D, 'card-no-photo.png'),
    ('30-card-photo-picker', '诗笺 · 选择真实观察照片', D, 'card-photo-picker.png'),
    ('31-save', '诗笺 · PNG 保存后', D, 'png-save-success.png'),
    ('32-saf', '系统文件保存位置选择器', D, 'saf-file-picker.png'),
    ('33-share-receiver', '系统分享 · 文件接收验证（测试接收器）', 'phase6a/journey', 'phase6-share-receiver.png'),
    ('34-offline-result', '断网 · 真实离线模型回退', ONLINE, 'plantnet-network-down-real-offline-fallback.png'),
    ('35-map-no-network', '观察地图 · 网络不可用', ONLINE, 'google-map-no-network-feedback.png'),
    ('36-map-retry', '观察地图 · 无网络时重试', ONLINE, 'google-map-no-network-retry.png'),
    ('37-permission-request', '系统相机权限申请', 'phase6a/permissions', '01-first-request.png'),
    ('38-permission-denied', '相机权限拒绝', 'phase6a/permissions', '02-denied.png'),
    ('39-observation-edit', '观察编辑 · 已有照片与备注', D, 'observation-editor-edit.png'),
    ('40-observation-camera', '观察编辑 · 相机拍摄入口', D, 'observation-editor-camera.png'),
]
# Replace older permission / system-share screenshots with latest functional evidence.
RELATED = [(s, t, 'phase6a/journey', 'phase6-share-chooser.png') if s == '19-system-share'
           else (s, t, 'phase6a/permissions', '02-denied.png') if s == '20-scan-permission-denied'
           else (s, t, d, f) for s, t, d, f in RELATED]
NARROW = [(s, t, GALLERY, 'gallery-320.png') if s == '14-gallery' else (s, t, d, f)
          for s, t, d, f in previous.GROUPS[2][2]]
NARROW.append(('19-gallery-missing', '观察照片集 · 照片文件丢失', GALLERY, 'gallery-missing-320.png'))
GROUPS = [
    ('01-pages', '全部主要页面', CORE),
    ('02-details-and-states', '滚动内容、弹窗与状态', RELATED),
    ('03-narrow-320dp', '320dp 窄屏与边界状态', NARROW),
    ('04-tablet', '平板布局', previous.GROUPS[3][2]),
    ('05-exported-artworks', '实际导出的 PNG 作品 · 非页面截图', previous.GROUPS[5][2]),
]

NOTE = ('当前页面的真实运行截图汇总：包含今日补拍、最新功能回归取证及未改版页面的既有最终视觉截图，'
        '不是声称全部在今天由同一个 APK 重新拍摄。照片集已使用最新独立改造版本，地图使用最新在线验证版本。'
        '测试档案、Debug 文字及文化审核提示均保留；授权参考摄影不是用户野外实拍，地图测试坐标不是实际 GPS。'
        '原图未经修图或裁切，总览仅将原图等比例缩小排版。')


def overview(entries, name, title):
    cols, cell_w, cell_h = 5, 290, 675
    sheet = Image.new('RGB', (cols * cell_w + 40, ((len(entries) + cols - 1) // cols) * cell_h + 110), '#f8f8f1')
    draw = ImageDraw.Draw(sheet)
    font = 'C:/Windows/Fonts/msyh.ttc'
    draw.text((20, 15), title, font=ImageFont.truetype(font, 27), fill='#24452e')
    draw.text((20, 58), '真实运行原图汇总 · 跨次取证 · 测试数据保留标识 · 原图可在 index.html 点开', font=ImageFont.truetype(font, 16), fill='#6a7364')
    for i, e in enumerate(entries):
        x, y = 20 + i % cols * cell_w, 105 + i // cols * cell_h
        with Image.open(OUT / e['file']) as im:
            thumb = ImageOps.contain(im.convert('RGB'), (265, 590), Image.Resampling.LANCZOS)
            sheet.paste(thumb, (x + (265 - thumb.width) // 2, y))
        title_lines = [e['title'][j:j + 17] for j in range(0, len(e['title']), 17)]
        draw.multiline_text((x, y + 598), '\n'.join(title_lines), font=ImageFont.truetype(font, 17), fill='#24452e', spacing=4)
    buffer = io.BytesIO()
    sheet.save(buffer, 'PNG')
    previous.new_or_equal(OUT / name, buffer.getvalue())


def main():
    entries = []
    for folder, section, items in GROUPS:
        for slug, title, directory, filename in items:
            source = EVIDENCE / directory / filename
            with Image.open(source) as im:
                im.verify()
            with Image.open(source) as im:
                width, height = im.size
            entries.append(dict(section=section, title=title, file=f'{folder}/{slug}.png',
                                source=source.relative_to(ROOT).as_posix(), width=width, height=height,
                                sha256=hashlib.sha256(source.read_bytes()).hexdigest()))
    for entry in entries:
        previous.safe_copy(ROOT / entry['source'], OUT / entry['file'])
    sections, lines = [], ['# Wildflower Lens · 当前页面截图集', '', '整理日期：2026-10-14。', '', NOTE, '',
        '## 查看方式', '', '- `index.html`：离线浏览，支持按页面名称搜索，点击查看原图。',
        '- `overview-pages-1.png`、`overview-pages-2.png`：主要页面总览。',
        '- `manifest.json`：每张原图的来源、像素尺寸及 SHA-256。', '',
        '## 覆盖与限制', '',
        '覆盖当前导航的 17 个路由：Explore、Onboarding、Identify、My Garden、Care、Map、About、Plant Detail、'
        'Culture、Culture Review（Debug）、Seasons、Botanical Card、Leaf Health、Species Details、Capture Details、'
        'Observation Editor、Observation Gallery。另收录识别结果、单张照片、季节详情及系统保存/分享界面。', '',
        '这不是所有动态组合的穷举：首次欢迎引导仅收录欢迎页；系统权限弹窗随设备版本变化。'
        '诗笺完整页是扩展高度视口，普通手机需滚动；开发版文化审阅不是正式发布已批准资料。'
        '无联网图块的早期失败诊断、旧版照片集和 Phase 1 旧视觉二级页不作为主要页面收录。', '',
        '摄影授权说明见 `provenance/ASSET_ATTRIBUTION.md` 及相关来源说明。', '']
    for folder, title, _ in GROUPS:
        cards = []
        lines += [f'## {title}', '', '| 页面 | 原图 | 来源 |', '|---|---|---|']
        for e in [e for e in entries if e['section'] == title]:
            lines.append(f"| {e['title']} | [{e['width']}×{e['height']}]({e['file']}) | `{e['source']}` |")
            cards.append(f'<figure data-title="{html.escape(e["title"], quote=True)}"><a href="{e["file"]}">'
                         f'<img loading="lazy" src="{e["file"]}" alt="{html.escape(e["title"])}"></a>'
                         f'<figcaption>{html.escape(e["title"])}<small>{e["width"]} × {e["height"]}<br>'
                         f'{html.escape(e["source"])}</small></figcaption></figure>')
        lines.append('')
        sections.append(f'<section id="{folder}"><h2>{title}</h2><div class="grid">{"".join(cards)}</div></section>')
    page = ('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        '<title>Wildflower Lens · 当前页面截图</title><style>'
        'body{margin:0;background:#f8f8f1;color:#24452e;font-family:system-ui,"Microsoft YaHei",sans-serif}'
        'main{max-width:1600px;margin:auto;padding:30px}p{line-height:1.8;max-width:1150px}'
        '.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(235px,1fr));gap:22px}'
        'figure{margin:0;background:#fffdf9;padding:12px;border:1px solid #dce6d6;border-radius:10px}'
        'img{display:block;width:100%;height:510px;object-fit:contain;background:#eeeee6}'
        'small{display:block;color:#777;overflow-wrap:anywhere;font-size:11px;margin-top:8px}'
        'figcaption{line-height:1.6;margin-top:12px}a{color:inherit}section{margin-top:36px}'
        'input{box-sizing:border-box;padding:13px;width:min(100%,600px);font:inherit;border:1px solid #9eaa99;border-radius:8px}'
        'nav{display:flex;flex-wrap:wrap;gap:15px;margin:22px 0}[hidden]{display:none!important}'
        '@media(max-width:520px){main{padding:15px}img{height:450px}}</style><main>'
        f'<h1>Wildflower Lens · 当前页面截图</h1><p>{NOTE}</p>'
        f'<p>2026-10-14 · {len(entries) - 3} 张页面/状态截图 + 3 张实际导出作品 · '
        '<a href="README.md">范围与来源</a> · <a href="overview-pages-1.png">总览 1</a> · <a href="overview-pages-2.png">总览 2</a></p>'
        '<input id="search" type="search" placeholder="搜索页面，例如：花园、地图、诗笺、320dp" aria-label="搜索截图">'
        '<nav>' + ''.join(f'<a href="#{f}">{t}</a>' for f, t, _ in GROUPS) + '</nav>' + ''.join(sections) +
        '<script>document.querySelector("#search").addEventListener("input",e=>{const q=e.target.value.trim().toLowerCase();'
        'document.querySelectorAll("figure").forEach(f=>f.hidden=!f.dataset.title.toLowerCase().includes(q));'
        'document.querySelectorAll("section").forEach(s=>s.hidden=!s.querySelector("figure:not([hidden])"));});</script></main></html>')
    for name, value in [('README.md', '\n'.join(lines)), ('index.html', page),
                         ('manifest.json', json.dumps(entries, ensure_ascii=False, indent=2))]:
        previous.new_or_equal(OUT / name, (value + '\n').encode('utf-8'))
    overview(entries[:15], 'overview-pages-1.png', 'Wildflower Lens · 主要页面 01–15')
    overview(entries[15:22], 'overview-pages-2.png', 'Wildflower Lens · 主要页面 16–22')
    for relative in ['ASSET_ATTRIBUTION.md', 'evidence/visual-kit-v2/README.md', 'evidence/my-garden/README.md',
                     'evidence/observation-gallery/README.md',
                     'evidence/personal-detail/README.md', 'evidence/poetry-module/README.md', 'evidence/poetry-module/ASSETS.md']:
        previous.safe_copy(ROOT / 'docs/wildflower' / relative, OUT / 'provenance' / relative)
    # Only allow image and documentation artifacts. Never include APK, logs, settings, or secrets.
    files = sorted(p for p in OUT.rglob('*') if p.is_file())
    assert all(p.suffix.lower() in {'.png', '.md', '.json', '.html'} for p in files)
    keys = []
    properties = ROOT / 'local.properties'
    if properties.exists():
        for line in properties.read_text(encoding='utf-8').splitlines():
            if '=' in line and 'KEY' in line.split('=', 1)[0].upper():
                key = line.split('=', 1)[1].strip().strip('"')
                if len(key) >= 12:
                    keys.append(key.encode())
    for p in files:
        content = p.read_bytes()
        assert not any(key in content for key in keys), 'Sensitive value detected; archive not created.'
    with zipfile.ZipFile(ZIP, 'x', zipfile.ZIP_DEFLATED, compresslevel=3) as archive:
        for p in files:
            archive.write(p, Path(OUT.name) / p.relative_to(OUT))
    with zipfile.ZipFile(ZIP) as archive:
        assert archive.testzip() is None
    for e in entries:
        assert hashlib.sha256((OUT / e['file']).read_bytes()).hexdigest() == e['sha256']
    print(json.dumps(dict(screenshots=len(entries) - 3, artworks=3, pages=len(CORE),
                         output=str(OUT), zip=str(ZIP), zip_bytes=ZIP.stat().st_size,
                         integrity='all source hashes match; ZIP verified; no configured key values'), ensure_ascii=False))


if __name__ == '__main__':
    main()
